package com.cyprienbrisset.fukkatsunop.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

object DarkModeManager {

    // Reactive state for the current dark mode — collected by MainActivity for theming
    private val _isDarkFlow = MutableStateFlow(false)
    val isDarkFlow: StateFlow<Boolean> = _isDarkFlow.asStateFlow()

    /** Call once from MainActivity.onCreate() to seed the flow from the system/time. */
    fun initFromSystem(ctx: Context) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeDark = hour < 7 || hour >= 20
        _isDarkFlow.value = isDark(ctx).let { if (it != timeDark) it else timeDark }
    }

    private const val PREFS = "dark_mode_schedule"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_DARK_H = "dark_h"
    private const val KEY_DARK_M = "dark_m"
    private const val KEY_LIGHT_H = "light_h"
    private const val KEY_LIGHT_M = "light_m"

    const val ACTION_DARK = "com.cyprienbrisset.fukkatsunop.DARK_MODE_ON"
    const val ACTION_LIGHT = "com.cyprienbrisset.fukkatsunop.DARK_MODE_OFF"

    data class Schedule(
        val enabled: Boolean = false,
        val darkHour: Int = 20,
        val darkMin: Int = 0,
        val lightHour: Int = 7,
        val lightMin: Int = 0,
    )

    fun getSchedule(ctx: Context): Schedule {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Schedule(
            enabled = p.getBoolean(KEY_ENABLED, false),
            darkHour = p.getInt(KEY_DARK_H, 20),
            darkMin = p.getInt(KEY_DARK_M, 0),
            lightHour = p.getInt(KEY_LIGHT_H, 7),
            lightMin = p.getInt(KEY_LIGHT_M, 0),
        )
    }

    fun saveSchedule(ctx: Context, schedule: Schedule) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, schedule.enabled)
            .putInt(KEY_DARK_H, schedule.darkHour)
            .putInt(KEY_DARK_M, schedule.darkMin)
            .putInt(KEY_LIGHT_H, schedule.lightHour)
            .putInt(KEY_LIGHT_M, schedule.lightMin)
            .apply()
        if (schedule.enabled) reschedule(ctx, schedule) else cancelAll(ctx)
    }

    fun apply(ctx: Context, dark: Boolean) {
        _isDarkFlow.value = dark
        // Immediate in-app effect (no permission needed)
        runCatching {
            (ctx.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager)
                .setApplicationNightMode(if (dark) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO)
        }
        // System-wide effect (needs WRITE_SECURE_SETTINGS, already granted)
        runCatching {
            Settings.Secure.putInt(ctx.contentResolver, "ui_night_mode", if (dark) 2 else 1)
        }
    }

    fun isDark(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun reschedule(ctx: Context, schedule: Schedule = getSchedule(ctx)) {
        cancelAll(ctx)
        if (!schedule.enabled) return
        val am = ctx.getSystemService(AlarmManager::class.java)
        setAlarm(ctx, am, ACTION_DARK, schedule.darkHour, schedule.darkMin)
        setAlarm(ctx, am, ACTION_LIGHT, schedule.lightHour, schedule.lightMin)
    }

    private fun setAlarm(ctx: Context, am: AlarmManager, action: String, hour: Int, min: Int) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, min)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val pi = pendingIntent(ctx, action)
        am.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
    }

    private fun cancelAll(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        listOf(ACTION_DARK, ACTION_LIGHT).forEach { action ->
            am.cancel(pendingIntent(ctx, action))
        }
    }

    private fun pendingIntent(ctx: Context, action: String) = PendingIntent.getBroadcast(
        ctx, action.hashCode(),
        Intent(action, null, ctx, DarkModeReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
