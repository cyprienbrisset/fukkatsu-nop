package com.cyprienbrisset.fukkatsunop.system

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

object DndController {
    fun isGranted(ctx: Context): Boolean =
        ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    fun isDndOn(ctx: Context): Boolean {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        return nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    }

    fun toggleOrRequest(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            ctx.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        nm.setInterruptionFilter(
            if (isDndOn(ctx)) NotificationManager.INTERRUPTION_FILTER_ALL
            else NotificationManager.INTERRUPTION_FILTER_NONE
        )
    }

    fun enableFor(ctx: Context, durationMillis: Long) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        val am = ctx.getSystemService(android.app.AlarmManager::class.java)
        val pi = offPending(ctx)
        am.cancel(pi)
        if (durationMillis > 0L) {
            am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + durationMillis, pi)
        }
    }

    fun disable(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        ctx.getSystemService(android.app.AlarmManager::class.java).cancel(offPending(ctx))
    }

    private fun offPending(ctx: Context): android.app.PendingIntent =
        android.app.PendingIntent.getBroadcast(
            ctx, 7001,
            Intent(ctx, DndOffReceiver::class.java).setAction(DndOffReceiver.ACTION),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
}
