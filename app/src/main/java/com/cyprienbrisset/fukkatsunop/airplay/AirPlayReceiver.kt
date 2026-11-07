package com.cyprienbrisset.fukkatsunop.airplay

import android.content.Context
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AirPlayReceiver {
    private val _state = MutableStateFlow<AirPlayState>(AirPlayState.Waiting)
    val state: StateFlow<AirPlayState> = _state

    /** True while the receiver is advertised on the network (Mac can see the Portal). */
    private val _advertising = MutableStateFlow(false)
    val advertising: StateFlow<Boolean> = _advertising

    private var mdns: MdnsAdvertiser? = null
    private var server: AirPlayHttpServer? = null
    private val renderer = H264Renderer()

    fun start(context: Context) {
        _state.value = AirPlayState.Waiting
        _advertising.value = true
        mdns = MdnsAdvertiser(context).also { it.start() }
        AirPlayPairing.init(context)
        server = AirPlayHttpServer(
            serverName = AirPlayPrefs.getName(context),
            onConnecting = { _state.value = AirPlayState.Connecting },
            onSession = { isExtended ->
                _state.value = AirPlayState.Streaming(isExtended)
            },
            onDisconnect = {
                renderer.detachSurface()
                _state.value = AirPlayState.Waiting
            },
            onCodecConfig = { cfg -> renderer.setConfig(cfg) },
            onVideoFrame = { frame, key -> renderer.pushFrame(frame, key) },
        ).also { it.start() }
    }

    fun stop() {
        server?.stop(); server = null
        mdns?.stop(); mdns = null
        renderer.stopCodec()
        _state.value = AirPlayState.Waiting
        _advertising.value = false
    }

    /** True once mirrored video is actually on screen. */
    val receivingVideo: StateFlow<Boolean> get() = renderer.firstFrame

    fun attachSurface(surface: Surface) = renderer.attachSurface(surface)
    fun detachSurface() = renderer.detachSurface()

    fun disconnect() {
        renderer.detachSurface()
        _state.value = AirPlayState.Waiting
    }
}
