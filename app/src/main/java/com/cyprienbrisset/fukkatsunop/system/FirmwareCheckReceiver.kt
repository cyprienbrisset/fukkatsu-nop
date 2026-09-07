package com.cyprienbrisset.fukkatsunop.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class FirmwareCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        FirmwareWatcher.check(context)
    }
}
