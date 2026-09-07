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

        // Arrêter le lever de soleil pré-alarme s'il est en cours
        SunriseForegroundService.stop(context)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AlarmRepository(AppDatabase.get(context).alarmDao())
                val scheduler = AlarmScheduler(context)
                val alarm = repo.byId(alarmId)
                AlarmForegroundService.start(context, alarmId, alarm?.label ?: "", alarm?.ringtoneUri)
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
