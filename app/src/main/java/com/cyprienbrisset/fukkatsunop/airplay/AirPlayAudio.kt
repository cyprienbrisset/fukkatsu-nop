package com.cyprienbrisset.fukkatsunop.airplay

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

/**
 * AirPlay real-time audio (stream type 96), as UxPlay handles it (lib/raop_rtp.c, raop_buffer.c):
 * RTP over UDP, each payload AES-128-CBC encrypted from the same key and IV for every packet,
 * the trailing partial block left in clear.
 *
 * Codec ("ct" in the SETUP stream): 2 = ALAC (music from an iPhone or a Mac),
 * 4 = AAC-LC, 8 = AAC-ELD (sound of a Mac screen mirroring).
 */
data class AudioStreamSpec(val compression: Int, val samplesPerFrame: Int, val sampleRate: Int = 44100) {
    val name get() = when (compression) { 2 -> "ALAC"; 4 -> "AAC-LC"; 8 -> "AAC-ELD"; 1 -> "PCM"; else -> "ct=$compression" }

    companion object {
        fun from(stream: Map<String, Any?>): AudioStreamSpec {
            val ct = (stream["ct"] as? Long)?.toInt() ?: 2
            val spf = (stream["spf"] as? Long)?.toInt() ?: if (ct == 8) 480 else 352
            val sr = (stream["sr"] as? Long)?.toInt() ?: 44100
            return AudioStreamSpec(ct, spf, sr)
        }
    }
}

object AudioCrypto {
    /** Decrypts one payload: whole 16-byte blocks with AES-CBC (IV reset per packet), the rest copied. */
    fun decrypt(key: ByteArray, iv: ByteArray, data: ByteArray, off: Int, len: Int): ByteArray {
        val out = data.copyOfRange(off, off + len)
        val blocks = len / 16 * 16
        if (blocks > 0) {
            val c = Cipher.getInstance("AES/CBC/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            c.doFinal(data, off, blocks, out, 0)
        }
        return out
    }
}

/** Puts RTP packets back in sequence order; gives up on a missing packet once [depth] newer ones wait. */
class RtpReorder(private val depth: Int = 16) {
    private val pending = HashMap<Int, ByteArray>()
    private var next = -1

    private fun diff(a: Int, b: Int) = ((a - b + 0x10000 + 0x8000) and 0xFFFF) - 0x8000

    fun push(seq: Int, payload: ByteArray, emit: (ByteArray) -> Unit) {
        if (next < 0) next = seq
        val d = diff(seq, next)
        // Far jump either way: the sender restarted its sequence.
        if (d < -64 || d > 1000) { pending.clear(); next = seq } else if (d < 0) return // already played
        pending[seq] = payload
        while (true) {
            val p = pending.remove(next)
            if (p != null) {
                emit(p); next = (next + 1) and 0xFFFF
            } else if (pending.size >= depth) {
                // A packet is lost: go on from the oldest one we have.
                next = pending.keys.minBy { diff(it, next) }
            } else return
        }
    }

    fun reset() { pending.clear(); next = -1 }
}

/**
 * One audio stream: data + control sockets, decoder and AudioTrack.
 * [keys] lists candidate AES keys; with ALAC the first one that yields a valid frame is kept.
 */
class AirPlayAudioSession(
    private val spec: AudioStreamSpec,
    private val keys: List<ByteArray>,
    private val iv: ByteArray?,
) {
    private val data = DatagramSocket(null).apply { bind(InetSocketAddress("::", 0)) }
    private val control = DatagramSocket(null).apply { bind(InetSocketAddress("::", 0)) }
    val dataPort: Int get() = data.localPort
    val controlPort: Int get() = control.localPort

    @Volatile private var running = true
    private val queue = LinkedBlockingQueue<ByteArray>(512)
    private val reorder = RtpReorder()
    private var key: ByteArray? = if (spec.compression == 2) null else keys.firstOrNull()

    @Volatile private var gain = 1f
    private var track: AudioTrack? = null

    fun start() {
        Timber.d("Audio: ${spec.name} spf=${spec.samplesPerFrame} sr=${spec.sampleRate} data=$dataPort control=$controlPort keys=${keys.size} iv=${iv != null}")
        receiver(data, "airplay-audio-data").start()
        receiver(control, "airplay-audio-ctrl").start()
        Thread(::playLoop, "airplay-audio-play").start()
    }

    fun stop() {
        running = false
        runCatching { data.close() }
        runCatching { control.close() }
        queue.offer(ByteArray(0))
    }

    /** Pause / seek: drop what is buffered. */
    fun flush() {
        synchronized(reorder) { reorder.reset() }
        queue.clear()
        runCatching { track?.pause(); track?.flush(); track?.play() }
    }

    /** AirPlay volume in dB: -144 = mute, else -30 (min) .. 0 (max). */
    fun setVolume(db: Float) {
        gain = when {
            db <= -144f -> 0f
            else -> 10f.pow(db.coerceIn(-30f, 0f) * 2f / 20f)
        }
        runCatching { track?.setVolume(gain) }
    }

    private fun receiver(sock: DatagramSocket, name: String) = Thread({
        val buf = ByteArray(4096)
        val pkt = DatagramPacket(buf, buf.size)
        var count = 0
        while (running) {
            try {
                sock.receive(pkt)
            } catch (e: Exception) {
                if (running) Timber.w("Audio: $name stopped: ${e.message}")
                break
            }
            val len = pkt.length
            if (len < 4) continue
            val type = buf[1].toInt() and 0x7F
            when {
                type == 0x60 && len > 12 -> handleRtp(buf, 0, len)
                // Retransmitted packet: 4-byte header then the original RTP packet.
                type == 0x56 && len > 16 -> handleRtp(buf, 4, len - 4)
                else -> Unit // 0x54 sync, timing: not needed to play
            }
            if (++count == 1) Timber.d("Audio: first packet on $name (type=0x${type.toString(16)} len=$len)")
        }
    }, name)

    private fun handleRtp(buf: ByteArray, off: Int, len: Int) {
        val seq = ((buf[off + 2].toInt() and 0xFF) shl 8) or (buf[off + 3].toInt() and 0xFF)
        val payload = buf.copyOfRange(off + 12, off + len)
        synchronized(reorder) { reorder.push(seq, payload) { queue.offer(it) } }
    }

    private fun playLoop() {
        try {
            when (spec.compression) {
                2 -> playAlac()
                4, 8 -> playAac()
                else -> Timber.e("Audio: unsupported codec ${spec.name}")
            }
        } catch (e: Exception) {
            if (running) Timber.e(e, "Audio: playback stopped")
        } finally {
            runCatching { track?.stop() }
            runCatching { track?.release() }
            track = null
        }
    }

    private fun openTrack(channels: Int): AudioTrack {
        val mask = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val min = AudioTrack.getMinBufferSize(spec.sampleRate, mask, AudioFormat.ENCODING_PCM_16BIT)
        return AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(spec.sampleRate).setChannelMask(mask).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(min * 4, spec.sampleRate * channels * 2 / 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also { it.setVolume(gain); it.play(); track = it }
    }

    private fun nextPacket(): ByteArray? {
        while (running) {
            val p = queue.poll(200, TimeUnit.MILLISECONDS) ?: continue
            return if (p.isEmpty()) null else p
        }
        return null
    }

    private fun clear(k: ByteArray?, p: ByteArray) =
        if (k == null || k.isEmpty() || iv == null) p else AudioCrypto.decrypt(k, iv, p, 0, p.size)

    private fun playAlac() {
        val cfg = AlacConfig(frameLength = spec.samplesPerFrame, sampleRate = spec.sampleRate)
        val decoder = AlacDecoder(cfg)
        val pcm = ShortArray(cfg.frameLength * cfg.numChannels)
        val at = openTrack(cfg.numChannels)
        var frames = 0
        var errors = 0
        while (running) {
            val p = nextPacket() ?: break
            if (key == null) key = pickAlacKey(p, decoder, pcm) ?: continue
            val n = decoder.decode(clear(key, p), p.size, pcm)
            if (n <= 0) {
                if (++errors <= 5 || errors % 500 == 0) Timber.w("Audio: undecodable ALAC frame #$errors (${p.size} bytes)")
                continue
            }
            at.write(pcm, 0, n * cfg.numChannels)
            if (++frames == 1 || frames % 2000 == 0) Timber.d("Audio: $frames ALAC frames played")
        }
    }

    /** The key that turns this packet into a valid ALAC frame (and the order tells which derivation the sender used). */
    private fun pickAlacKey(p: ByteArray, decoder: AlacDecoder, pcm: ShortArray): ByteArray? {
        // A frame starts with a mono or stereo element (3-bit tag 0 or 1, 4-bit tag, 12 zero bits).
        fun plausible(f: ByteArray) = f.size > 3 && (f[0].toInt() and 0xC1) == 0 && f[1].toInt() == 0 &&
            (f[2].toInt() and 0xE0) == 0 && decoder.decode(f, f.size, pcm) > 0
        keys.forEachIndexed { i, k ->
            if (plausible(clear(k, p))) { Timber.d("Audio: ALAC key #$i works"); return k }
        }
        if (plausible(p)) { Timber.d("Audio: ALAC stream is not encrypted"); return ByteArray(0) }
        Timber.w("Audio: no key decodes the ALAC stream yet")
        return null
    }

    private fun playAac() {
        val eld = spec.compression == 8
        // AudioSpecificConfig, 44.1 kHz stereo: AAC-ELD with 480-sample frames, or AAC-LC.
        val asc = if (eld) byteArrayOf(0xF8.toByte(), 0xE8.toByte(), 0x50, 0x00) else byteArrayOf(0x12, 0x10)
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, spec.sampleRate, 2).apply {
            setByteBuffer("csd-0", ByteBuffer.wrap(asc))
            if (eld) setInteger(MediaFormat.KEY_AAC_PROFILE, 39)
        }
        val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, 0)
        codec.start()
        val at = openTrack(2)
        val info = MediaCodec.BufferInfo()
        var chunk = ByteArray(0)
        var frames = 0
        try {
            while (running) {
                val p = nextPacket() ?: break
                val au = clear(key, p)
                val inIdx = codec.dequeueInputBuffer(20_000)
                if (inIdx >= 0) {
                    codec.getInputBuffer(inIdx)!!.apply { clear(); put(au) }
                    codec.queueInputBuffer(inIdx, 0, au.size, 0, 0)
                }
                while (true) {
                    val outIdx = codec.dequeueOutputBuffer(info, 0)
                    if (outIdx < 0) break
                    val ob = codec.getOutputBuffer(outIdx)!!
                    if (chunk.size < info.size) chunk = ByteArray(info.size)
                    ob.position(info.offset); ob.get(chunk, 0, info.size)
                    at.write(chunk, 0, info.size)
                    codec.releaseOutputBuffer(outIdx, false)
                    if (++frames == 1 || frames % 2000 == 0) Timber.d("Audio: $frames ${spec.name} frames played")
                }
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
        }
    }
}
