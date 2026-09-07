package com.cyprienbrisset.fukkatsunop.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SunriseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val alarmId = intent.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, -1)
        if (alarmId < 0) return
        SunriseForegroundService.start(context, alarmId)
    }
    companion object {
        const val ACTION_FIRE = "com.cyprienbrisset.fukkatsunop.SUNRISE_FIRE"
    }
}
