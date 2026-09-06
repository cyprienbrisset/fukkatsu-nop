package com.cyprienbrisset.myportal.airplay

sealed class AirPlayState {
    object Waiting    : AirPlayState()
    object Connecting : AirPlayState()
    object Streaming  : AirPlayState()
    data class Error(val msg: String) : AirPlayState()
}
