package com.cyprienbrisset.fukkatsunop.airplay

import android.content.Context
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AirPlayReceiver {
    private val _state = MutableStateFlow<AirPlayState>(AirPlayState.Waiting)
    val state: StateFlow<AirPlayState> = _state

    private var mdns: MdnsAdvertiser? = null
    private var server: AirPlayHttpServer? = null
    private val renderer = H264Renderer()

    fun start(context: Context) {
        _state.value = AirPlayState.Waiting
        mdns = MdnsAdvertiser(context).also { it.start() }
        server = AirPlayHttpServer(
            onConnecting = { _state.value = AirPlayState.Connecting },
            onSession = { isExtended ->
                _state.value = AirPlayState.Streaming(isExtended)
            },
            onDisconnect = {
                renderer.detachSurface()
                _state.value = AirPlayState.Waiting
            },
            onVideoNal = { nal -> renderer.pushNalUnit(nal) },
            onReconfigureCsd = { sps, pps -> renderer.reconfigureCsd(sps, pps) },
        ).also { it.start() }
    }

    fun stop() {
        server?.stop(); server = null
        mdns?.stop(); mdns = null
        renderer.stopCodec()
        _state.value = AirPlayState.Waiting
    }

    fun attachSurface(surface: Surface) = renderer.attachSurface(surface)
    fun detachSurface() = renderer.detachSurface()

    fun disconnect() {
        renderer.detachSurface()
        _state.value = AirPlayState.Waiting
    }
}
