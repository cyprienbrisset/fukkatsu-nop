package com.cyprienbrisset.fukkatsunop

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.util.Log

class MyPortalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        disablePackageVerifier()
        installCrashWatchdog()
    }

    private fun disablePackageVerifier() {
        runCatching {
            Settings.Global.putInt(contentResolver, "package_verifier_enable", 0)
            Settings.Global.putInt(contentResolver, "verifier_verify_adb_installs", 0)
            Settings.Global.putInt(contentResolver, "package_verifier_user_consent", -1)
        }.onFailure { Log.w("MyPortalApp", "verifier disable failed: ${it.message}") }
    }

    private fun installCrashWatchdog() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e("Watchdog", "Crash détecté — redémarrage dans 1s", throwable)
                val restart = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val pi = PendingIntent.getActivity(
                    this, 0, restart,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
                )
                val am = getSystemService(ALARM_SERVICE) as AlarmManager
                am.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 1_000L,
                    pi,
                )
            } catch (_: Exception) {
                // ne pas bloquer la propagation si le watchdog lui-même échoue
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
