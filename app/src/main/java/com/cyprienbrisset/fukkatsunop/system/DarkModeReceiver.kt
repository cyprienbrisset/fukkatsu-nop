package com.cyprienbrisset.fukkatsunop.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DarkModeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            DarkModeManager.ACTION_DARK -> DarkModeManager.apply(ctx, dark = true)
            DarkModeManager.ACTION_LIGHT -> DarkModeManager.apply(ctx, dark = false)
        }
        // Reschedule for next occurrence (same time next day)
        DarkModeManager.reschedule(ctx)
    }
}
