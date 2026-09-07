package com.cyprienbrisset.fukkatsunop.airplay

sealed class AirPlayState {
    object Waiting    : AirPlayState()
    object Connecting : AirPlayState()
    data class Streaming(val isExtended: Boolean = false) : AirPlayState()
    data class Error(val msg: String) : AirPlayState()
}
