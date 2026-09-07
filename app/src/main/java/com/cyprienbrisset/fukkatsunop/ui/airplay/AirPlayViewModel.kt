package com.cyprienbrisset.fukkatsunop.ui.airplay

import androidx.lifecycle.ViewModel
import com.cyprienbrisset.fukkatsunop.airplay.AirPlayReceiver
import com.cyprienbrisset.fukkatsunop.airplay.AirPlayState

class AirPlayViewModel : ViewModel() {
    val state = AirPlayReceiver.state

    fun disconnect() = AirPlayReceiver.disconnect()
}
