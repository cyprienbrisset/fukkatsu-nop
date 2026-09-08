package com.cyprienbrisset.fukkatsunop.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cyprienbrisset.fukkatsunop.data.AppDatabase
import com.cyprienbrisset.fukkatsunop.data.alarm.AlarmRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1)
        if (alarmId < 0) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AlarmRepository(AppDatabase.get(context).alarmDao())
                val scheduler = AlarmScheduler(context)
                val alarm = repo.byId(alarmId)
                val videoEnabled = alarm?.videoEnabled ?: false
                val volumeProgressive = alarm?.volumeProgressive ?: true
                AlarmForegroundService.start(context, alarmId, alarm?.label ?: "", alarm?.ringtoneUri, videoEnabled, volumeProgressive)
                // Lancement explicite de l'activité : le fullScreenIntent de la notif
                // ne déclenche pas l'écran automatiquement sur certains appareils (Gen 1).
                context.startActivity(
                    Intent(context, AlarmRingActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                        .putExtra(EXTRA_ALARM_ID, alarmId)
                        .putExtra(AlarmForegroundService.EXTRA_VIDEO_ENABLED, videoEnabled)
                )
                if (alarm != null) {
                    if (alarm.repeatDays == 0) repo.setEnabled(alarm, false)
                    else scheduler.schedule(alarm)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.cyprienbrisset.fukkatsunop.ALARM_FIRE"
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
