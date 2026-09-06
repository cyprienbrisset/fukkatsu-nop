package com.cyprienbrisset.myportal.ui.airplay

import androidx.lifecycle.ViewModel
import com.cyprienbrisset.myportal.airplay.AirPlayReceiver
import com.cyprienbrisset.myportal.airplay.AirPlayState

class AirPlayViewModel : ViewModel() {
    val state = AirPlayReceiver.state

    fun disconnect() = AirPlayReceiver.disconnect()
}
