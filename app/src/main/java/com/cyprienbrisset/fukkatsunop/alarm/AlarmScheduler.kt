package com.cyprienbrisset.fukkatsunop.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.cyprienbrisset.fukkatsunop.data.alarm.AlarmEntity
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

/** Pure function: next fire time for [alarm] relative to [from]. bit0=Mon..bit6=Sun. */
fun nextTriggerTime(alarm: AlarmEntity, from: LocalDateTime): LocalDateTime {
    val todayAt = from.toLocalDate().atTime(alarm.hour, alarm.minute)
    if (alarm.repeatDays == 0) {
        return if (todayAt.isAfter(from)) todayAt else todayAt.plusDays(1)
    }
    for (offset in 0..7) {
        val candidateDate = from.toLocalDate().plusDays(offset.toLong())
        val bit = candidateDate.dayOfWeek.bitIndex()
        val matches = (alarm.repeatDays and (1 shl bit)) != 0
        if (matches) {
            val candidate = candidateDate.atTime(alarm.hour, alarm.minute)
            if (candidate.isAfter(from)) return candidate
        }
    }
    return todayAt.plusWeeks(1)
}

private fun DayOfWeek.bitIndex(): Int = this.value - 1 // MONDAY(1)->0 .. SUNDAY(7)->6

class AlarmScheduler(private val context: Context) {
    private val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(alarm: AlarmEntity) {
        if (!alarm.enabled) { cancel(alarm.id); return }
        val trigger = nextTriggerTime(alarm, LocalDateTime.now())
        val triggerMillis = trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val show = PendingIntent.getActivity(
            context, alarm.id.toInt(),
            Intent(context, AlarmRingActivity::class.java).putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerMillis, show), firePendingIntent(alarm.id))
    }

    fun cancel(alarmId: Long) = am.cancel(firePendingIntent(alarmId))

    private fun firePendingIntent(alarmId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getBroadcast(
            context, alarmId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

object AlarmSnooze {
    fun schedule(context: Context, alarmId: Long, minutes: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerMillis = System.currentTimeMillis() + minutes * 60_000L
        val fire = PendingIntent.getBroadcast(
            context, alarmId.toInt(),
            Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_FIRE
                putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val show = PendingIntent.getActivity(
            context, alarmId.toInt(),
            Intent(context, AlarmRingActivity::class.java).putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerMillis, show), fire)
    }
}
