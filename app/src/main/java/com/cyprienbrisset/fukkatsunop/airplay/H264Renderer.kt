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

    // Called when SPS + PPS arrive from the stream — reconfigures the codec with real CSD.
    fun reconfigureCsd(sps: ByteArray, pps: ByteArray) {
        val s = surface ?: return
        stopCodec()
        val format = MediaFormat.createVideoFormat("video/avc", 1920, 1080)
        format.setByteBuffer("csd-0", java.nio.ByteBuffer.wrap(sps))
        format.setByteBuffer("csd-1", java.nio.ByteBuffer.wrap(pps))
        val c = MediaCodec.createDecoderByType("video/avc")
        c.configure(format, s, null, 0)
        c.start()
        codec = c
        configured = true
    }

    fun pushNalUnit(data: ByteArray) {
        if (!configured) return
        val codec = codec ?: return
        val startOff = if (data.size >= 4 && data[0] == 0.toByte() && data[3] == 1.toByte()) 4
                       else if (data.size >= 3 && data[0] == 0.toByte() && data[2] == 1.toByte()) 3
                       else 0
        val nalType = if (data.size > startOff) (data[startOff].toInt() and 0x1F) else 0
        val flags = if (nalType == 5) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
        val index = codec.dequeueInputBuffer(10_000L)
        if (index < 0) return
        val buf = codec.getInputBuffer(index) ?: return
        buf.clear()
        buf.put(data)
        codec.queueInputBuffer(index, 0, data.size, System.nanoTime() / 1000, flags)
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
