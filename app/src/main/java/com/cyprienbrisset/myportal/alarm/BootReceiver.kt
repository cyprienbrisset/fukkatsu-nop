package com.cyprienbrisset.myportal.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cyprienbrisset.myportal.data.AppDatabase
import com.cyprienbrisset.myportal.data.alarm.AlarmRepository
import com.cyprienbrisset.myportal.system.DarkModeManager
import com.cyprienbrisset.myportal.system.FirmwareWatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        DarkModeManager.reschedule(context)
        FirmwareWatcher.init(context)
        FirmwareWatcher.scheduleDaily(context)
        context.startForegroundService(
            Intent(context, com.cyprienbrisset.myportal.airplay.AirPlayService::class.java)
        )
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AlarmRepository(AppDatabase.get(context).alarmDao())
                val scheduler = AlarmScheduler(context)
                repo.enabled().forEach { scheduler.schedule(it) }
            } finally {
                pending.finish()
            }
        }
    }
}
