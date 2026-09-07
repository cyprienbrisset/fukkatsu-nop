package com.cyprienbrisset.fukkatsunop.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cyprienbrisset.fukkatsunop.data.AppDatabase
import com.cyprienbrisset.fukkatsunop.data.alarm.AlarmRepository
import com.cyprienbrisset.fukkatsunop.system.DarkModeManager
import com.cyprienbrisset.fukkatsunop.system.FirmwareWatcher
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
            Intent(context, com.cyprienbrisset.fukkatsunop.airplay.AirPlayService::class.java)
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
