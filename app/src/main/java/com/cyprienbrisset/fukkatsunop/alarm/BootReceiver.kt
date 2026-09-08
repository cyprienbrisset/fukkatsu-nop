package com.cyprienbrisset.fukkatsunop.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.cyprienbrisset.fukkatsunop.data.AppDatabase
import com.cyprienbrisset.fukkatsunop.data.alarm.AlarmRepository
import com.cyprienbrisset.fukkatsunop.overlay.OverlayService
import com.cyprienbrisset.fukkatsunop.system.DarkModeManager
import com.cyprienbrisset.fukkatsunop.system.FirmwareWatcher
import com.cyprienbrisset.fukkatsunop.system.UpdateCheckReceiver
import com.cyprienbrisset.fukkatsunop.system.UpdateChecker
import com.cyprienbrisset.fukkatsunop.system.voice.VoiceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        DarkModeManager.reschedule(context)
        FirmwareWatcher.init(context)
        FirmwareWatcher.scheduleDaily(context)
        UpdateChecker.scheduleDaily(context)
        VoiceService.start(context)
        context.startForegroundService(
            Intent(context, com.cyprienbrisset.fukkatsunop.airplay.AirPlayService::class.java)
        )
        // Auto-restart overlay on boot if the user already granted the permission.
        if (Settings.canDrawOverlays(context)) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AlarmRepository(AppDatabase.get(context).alarmDao())
                val scheduler = AlarmScheduler(context)
                repo.enabled().forEach { scheduler.schedule(it) }
                UpdateChecker.check(context)
            } finally {
                pending.finish()
            }
        }
    }
}
