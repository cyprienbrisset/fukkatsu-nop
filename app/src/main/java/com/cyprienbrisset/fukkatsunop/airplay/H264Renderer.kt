package com.cyprienbrisset.fukkatsunop.airplay

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface

class H264Renderer {
    private var codec: MediaCodec? = null
    private var surface: Surface? = null
    @Volatile private var configured = false

    fun attachSurface(s: Surface) {
        surface = s
        startCodec(s)
    }

    fun detachSurface() {
        stopCodec()
        surface = null
    }

    fun pushNalUnit(data: ByteArray) {
        if (!configured) return
        val codec = codec ?: return
        val index = codec.dequeueInputBuffer(10_000L)
        if (index < 0) return
        val buf = codec.getInputBuffer(index) ?: return
        buf.clear()
        buf.put(data)
        codec.queueInputBuffer(index, 0, data.size, System.nanoTime() / 1000, 0)
        // Release output buffers so the decoder keeps rendering to the Surface.
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outIdx = codec.dequeueOutputBuffer(info, 0L)
            if (outIdx < 0) break
            codec.releaseOutputBuffer(outIdx, true)
        }
    }

    private fun startCodec(s: Surface) {
        stopCodec()
        val format = MediaFormat.createVideoFormat("video/avc", 1920, 1080)
        val c = MediaCodec.createDecoderByType("video/avc")
        c.configure(format, s, null, 0)
        c.start()
        codec = c
        configured = true
    }

    fun stopCodec() {
        configured = false
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
    }
}
