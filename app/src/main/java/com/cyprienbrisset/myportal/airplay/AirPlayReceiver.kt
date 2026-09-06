package com.cyprienbrisset.myportal.airplay

import android.content.Context
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AirPlayReceiver {
    private val _state = MutableStateFlow<AirPlayState>(AirPlayState.Waiting)
    val state: StateFlow<AirPlayState> = _state

    private var mdns: MdnsAdvertiser? = null
    private var server: AirPlayHttpServer? = null
    private var rtp: RtpVideoReceiver? = null
    private val renderer = H264Renderer()

    fun start(context: Context) {
        _state.value = AirPlayState.Waiting
        mdns = MdnsAdvertiser(context).also { it.start() }
        server = AirPlayHttpServer(
            onConnecting = { _state.value = AirPlayState.Connecting },
            onSession = { session ->
                rtp?.stop()
                rtp = RtpVideoReceiver(session) { nal -> renderer.pushNalUnit(nal) }
                    .also { it.start() }
                _state.value = AirPlayState.Streaming
            },
            onDisconnect = {
                rtp?.stop()
                rtp = null
                renderer.detachSurface()
                _state.value = AirPlayState.Waiting
            },
        ).also { it.start() }
    }

    fun stop() {
        rtp?.stop(); rtp = null
        server?.stop(); server = null
        mdns?.stop(); mdns = null
        renderer.stopCodec()
        _state.value = AirPlayState.Waiting
    }

    fun attachSurface(surface: Surface) = renderer.attachSurface(surface)
    fun detachSurface() = renderer.detachSurface()

    fun disconnect() {
        rtp?.stop(); rtp = null
        renderer.detachSurface()
        _state.value = AirPlayState.Waiting
        // Server keeps listening for next connection.
    }
}
