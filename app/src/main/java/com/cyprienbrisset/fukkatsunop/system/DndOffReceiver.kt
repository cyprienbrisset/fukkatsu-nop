package com.cyprienbrisset.fukkatsunop.system

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DndOffReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }
    companion object { const val ACTION = "com.cyprienbrisset.fukkatsunop.DND_OFF" }
}
