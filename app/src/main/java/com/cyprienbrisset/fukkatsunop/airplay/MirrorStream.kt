package com.cyprienbrisset.fukkatsunop.airplay

import timber.log.Timber
import java.io.InputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Key schedule of AirPlay screen mirroring, as implemented by UxPlay
 * (lib/raop_handlers.h and lib/mirror_buffer.c, GPL-3.0):
 *
 * 1. FairPlay-decrypt the 72-byte `ekey` of the first SETUP with the 164-byte
 *    `/fp-setup` key message → 16-byte session key.
 * 2. When pair-verify produced an X25519 shared secret, the session key becomes
 *    SHA-512(sessionKey ‖ ecdhSecret)[0..16].
 * 3. Each mirroring stream gets AES-128-CTR with
 *    key = SHA-512("AirPlayStreamKey<id>" ‖ sessionKey)[0..16] and
 *    iv  = SHA-512("AirPlayStreamIV<id>"  ‖ sessionKey)[0..16],
 *    <id> being the unsigned decimal `streamConnectionID` of the second SETUP.
 */
object MirrorKeys {
    fun sessionKey(fairPlayKey: ByteArray, ecdhSecret: ByteArray?): ByteArray =
        if (ecdhSecret == null) fairPlayKey.copyOf(16) else sha512(fairPlayKey.copyOf(16), ecdhSecret).copyOf(16)

    fun streamKey(sessionKey: ByteArray, streamConnectionId: Long): Pair<ByteArray, ByteArray> {
        val id = java.lang.Long.toUnsignedString(streamConnectionId)
        val key = sha512("AirPlayStreamKey$id".toByteArray(Charsets.US_ASCII), sessionKey).copyOf(16)
        val iv = sha512("AirPlayStreamIV$id".toByteArray(Charsets.US_ASCII), sessionKey).copyOf(16)
        return key to iv
    }

    /** One CTR keystream for the whole TCP stream: Cipher.update keeps the offset across packets. */
    fun videoCipher(sessionKey: ByteArray, streamConnectionId: Long): Cipher {
        val (key, iv) = streamKey(sessionKey, streamConnectionId)
        return Cipher.getInstance("AES/CTR/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        }
    }

    private fun sha512(vararg parts: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-512").run { parts.forEach { update(it) }; digest() }
}

/** H.264 parameter sets in Annex-B form (start code + NAL). */
class CodecConfig(val sps: ByteArray, val pps: ByteArray)

/**
 * Reads the mirroring data connection: 128-byte header then payload.
 * Header bytes 0-3 = payload size (little-endian), byte 4 = payload type:
 *   0x00 encrypted video (AVCC length-prefixed NALs), 0x01 SPS/PPS (avcC, clear),
 *   0x02 / 0x05 heartbeat and stats (ignored).
 */
class MirrorStreamReader(
    private val cipher: Cipher?,
    private val onConfig: (CodecConfig) -> Unit,
    private val onFrame: (frame: ByteArray, keyFrame: Boolean) -> Unit,
) {
    private var warnedNoKey = false
    private var frames = 0

    fun run(inp: InputStream) {
        val hdr = ByteArray(128)
        while (true) {
            if (!readFully(inp, hdr)) return
            val size = leInt(hdr, 0)
            if (size < 0 || size > 32 * 1024 * 1024) {
                Timber.e("Mirror: invalid payload size $size, closing")
                return
            }
            val payload = ByteArray(size)
            if (size > 0 && !readFully(inp, payload)) return
            handle(hdr[4].toInt() and 0xFF, payload)
        }
    }

    /** Visible for tests: processes one packet already split from the stream. */
    fun handle(type: Int, payload: ByteArray) {
        when (type) {
            0x00 -> {
                if (cipher == null) {
                    if (!warnedNoKey) Timber.e("Mirror: video received but no stream key (FairPlay/SETUP failed)")
                    warnedNoKey = true
                    return
                }
                val clear = cipher.update(payload) ?: return
                val frame = avccToAnnexB(clear)
                if (frame == null) {
                    Timber.w("Mirror: frame ${frames + 1} does not parse as AVCC after decryption (${payload.size} bytes)")
                    return
                }
                frames++
                if (frames == 1 || frames % 300 == 0) Timber.d("Mirror: $frames frames, last ${frame.size} bytes key=${containsIdr(frame)}")
                onFrame(frame, containsIdr(frame))
            }
            0x01 -> {
                if (payload.isEmpty()) {
                    Timber.e("Mirror: empty codec packet — client wants a codec other than H.264")
                    return
                }
                val cfg = parseAvcC(payload)
                if (cfg == null) Timber.w("Mirror: unreadable avcC (${payload.size} bytes)")
                else { Timber.d("Mirror: H.264 config sps=${cfg.sps.size} pps=${cfg.pps.size}"); onConfig(cfg) }
            }
            else -> Unit
        }
    }

    companion object {
        private val START = byteArrayOf(0, 0, 0, 1)

        fun leInt(b: ByteArray, off: Int): Int =
            (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8) or
                ((b[off + 2].toInt() and 0xFF) shl 16) or ((b[off + 3].toInt() and 0xFF) shl 24)

        private fun beInt(b: ByteArray, off: Int): Int =
            ((b[off].toInt() and 0xFF) shl 24) or ((b[off + 1].toInt() and 0xFF) shl 16) or
                ((b[off + 2].toInt() and 0xFF) shl 8) or (b[off + 3].toInt() and 0xFF)

        /** Replaces each 4-byte big-endian NAL length by a start code; null if the lengths don't add up. */
        fun avccToAnnexB(data: ByteArray): ByteArray? {
            val out = data.copyOf()
            var off = 0
            while (off + 4 <= data.size) {
                val len = beInt(data, off)
                if (len <= 0 || off + 4 + len > data.size) return null
                START.copyInto(out, off)
                off += 4 + len
            }
            return if (off == data.size) out else null
        }

        fun containsIdr(annexB: ByteArray): Boolean {
            var i = 0
            while (i + 4 < annexB.size) {
                if (annexB[i] == 0.toByte() && annexB[i + 1] == 0.toByte() && annexB[i + 2] == 0.toByte() && annexB[i + 3] == 1.toByte()) {
                    if ((annexB[i + 4].toInt() and 0x1F) == 5) return true
                    i += 4
                } else i++
            }
            return false
        }

        /** avcC: version, profile, compat, level, lengthSize, numSps, (len16, sps)*, numPps, (len16, pps)*. */
        fun parseAvcC(d: ByteArray): CodecConfig? {
            if (d.size < 8 || d[0].toInt() != 1) return null
            var p = 5
            val numSps = d[p++].toInt() and 0x1F
            if (numSps < 1 || p + 2 > d.size) return null
            val spsLen = ((d[p].toInt() and 0xFF) shl 8) or (d[p + 1].toInt() and 0xFF); p += 2
            if (p + spsLen > d.size) return null
            val sps = START + d.copyOfRange(p, p + spsLen); p += spsLen
            repeat(numSps - 1) {
                if (p + 2 > d.size) return null
                p += 2 + (((d[p].toInt() and 0xFF) shl 8) or (d[p + 1].toInt() and 0xFF))
            }
            if (p >= d.size) return null
            val numPps = d[p++].toInt() and 0xFF
            if (numPps < 1 || p + 2 > d.size) return null
            val ppsLen = ((d[p].toInt() and 0xFF) shl 8) or (d[p + 1].toInt() and 0xFF); p += 2
            if (p + ppsLen > d.size) return null
            return CodecConfig(sps, START + d.copyOfRange(p, p + ppsLen))
        }

        private fun readFully(inp: InputStream, buf: ByteArray): Boolean {
            var off = 0
            while (off < buf.size) {
                val r = inp.read(buf, off, buf.size - off)
                if (r < 0) return false
                off += r
            }
            return true
        }
    }
}
