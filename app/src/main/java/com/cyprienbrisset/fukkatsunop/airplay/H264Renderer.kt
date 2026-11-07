package com.cyprienbrisset.fukkatsunop.airplay

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Decodes the mirroring H.264 stream onto the AirPlayActivity surface.
 *
 * A Mac sends a single key frame when mirroring starts, then only P-frames: one lost
 * frame corrupts the picture for good. So no frame is ever dropped. Frames are decoded
 * on a dedicated thread (the network reader never waits for the decoder) and, when the
 * decoder falls behind, late pictures are decoded without being shown so the screen
 * catches up with the Mac at once instead of lagging.
 *
 * Frames since the last key frame are kept so decoding can start cleanly when the
 * surface arrives after the stream.
 */
class H264Renderer {
    private class Item(val data: ByteArray, val key: Boolean)

    private val lock = Any()
    private var surface: Surface? = null
    private var config: CodecConfig? = null
    private val sinceKeyFrame = ArrayList<ByteArray>()
    private var pendingBytes = 0

    @Volatile private var session: DecodeSession? = null

    /** True once the first picture reached the screen; the waiting overlay hides then. */
    val firstFrame = MutableStateFlow(false)

    fun attachSurface(s: Surface) = synchronized(lock) {
        surface = s
        restartLocked()
    }

    fun detachSurface() = synchronized(lock) {
        stopSessionLocked()
        surface = null
    }

    fun setConfig(c: CodecConfig) = synchronized(lock) {
        val same = config?.let { it.sps.contentEquals(c.sps) && it.pps.contentEquals(c.pps) } == true
        config = c
        if (!same) {
            sinceKeyFrame.clear(); pendingBytes = 0
            restartLocked()
        }
    }

    fun pushFrame(frame: ByteArray, keyFrame: Boolean) = synchronized(lock) {
        if (keyFrame) { sinceKeyFrame.clear(); pendingBytes = 0 }
        if (sinceKeyFrame.isNotEmpty() || keyFrame) {
            if (pendingBytes < 48 * 1024 * 1024) { sinceKeyFrame.add(frame); pendingBytes += frame.size }
        }
        session?.offer(Item(frame, keyFrame))
    }

    fun stopCodec() = synchronized(lock) {
        stopSessionLocked()
        config = null
        sinceKeyFrame.clear(); pendingBytes = 0
        firstFrame.value = false
    }

    private fun restartLocked() {
        stopSessionLocked()
        val s = surface ?: return
        val c = config ?: return
        val dec = runCatching {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080).apply {
                setByteBuffer("csd-0", ByteBuffer.wrap(c.sps))
                setByteBuffer("csd-1", ByteBuffer.wrap(c.pps))
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4 * 1024 * 1024)
            }
            MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(format, s, null, 0)
                start()
            }
        }.onFailure { Timber.e(it, "H264Renderer: decoder start failed") }.getOrNull() ?: return
        val ses = DecodeSession(dec)
        // Replay the current group of pictures first: references must be decoded, and the
        // catch-up logic shows only the most recent of them.
        sinceKeyFrame.forEachIndexed { i, f -> ses.offer(Item(f, i == 0)) }
        session = ses
        ses.start()
    }

    private fun stopSessionLocked() {
        session?.shutdown()
        session = null
    }

    private inner class DecodeSession(private val codec: MediaCodec) : Thread("airplay-decoder") {
        private val queue = LinkedBlockingQueue<Item>()
        @Volatile private var running = true
        private val info = MediaCodec.BufferInfo()
        private var rendered = 0

        fun offer(item: Item) { queue.offer(item) }

        fun shutdown() {
            running = false
            interrupt()
            runCatching { join(500) }
            runCatching { codec.stop() }
            runCatching { codec.release() }
        }

        override fun run() {
            try {
                while (running) {
                    val item = queue.poll(20, TimeUnit.MILLISECONDS)
                    if (item != null) feed(item)
                    drain()
                }
            } catch (_: InterruptedException) {
            } catch (e: Exception) {
                if (running) Timber.w("H264Renderer: decode loop stopped: ${e.message}")
            }
        }

        /** Waits for an input buffer instead of dropping the frame. */
        private fun feed(item: Item) {
            while (running) {
                val index = codec.dequeueInputBuffer(10_000L)
                if (index < 0) { drain(); continue }
                val buf = codec.getInputBuffer(index) ?: return
                buf.clear()
                if (item.data.size > buf.capacity()) {
                    Timber.w("H264Renderer: frame too big ${item.data.size}")
                    codec.queueInputBuffer(index, 0, 0, 0, 0)
                    return
                }
                buf.put(item.data)
                codec.queueInputBuffer(index, 0, item.data.size, System.nanoTime() / 1000,
                    if (item.key) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                return
            }
        }

        /** Shows a decoded picture only when no newer frame is waiting: catches up instead of lagging. */
        private fun drain() {
            while (running) {
                val out = codec.dequeueOutputBuffer(info, 0L)
                if (out < 0) return
                val show = queue.size <= 1
                codec.releaseOutputBuffer(out, show)
                if (show) {
                    rendered++
                    if (rendered == 1) firstFrame.value = true
                    if (rendered == 1 || rendered % 300 == 0) Timber.d("H264Renderer: $rendered frames rendered, backlog ${queue.size}")
                }
            }
        }
    }
}
