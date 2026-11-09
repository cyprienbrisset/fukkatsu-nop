package com.cyprienbrisset.fukkatsunop

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import timber.log.Timber

class MyPortalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        else Timber.plant(ReleaseTree())
        disablePackageVerifier()
        installCrashWatchdog()
    }

    private fun disablePackageVerifier() {
        runCatching {
            Settings.Global.putInt(contentResolver, "package_verifier_enable", 0)
            Settings.Global.putInt(contentResolver, "verifier_verify_adb_installs", 0)
            Settings.Global.putInt(contentResolver, "package_verifier_user_consent", -1)
        }.onFailure { Timber.w(it, "verifier disable failed") }
    }

    private fun installCrashWatchdog() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Timber.e(throwable, "Crash détecté — redémarrage dans 1s")
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
            } catch (_: Exception) { }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}

/** Release builds keep info, warnings and errors in logcat, so a Portal can be diagnosed over adb. */
private class ReleaseTree : Timber.Tree() {
    override fun isLoggable(tag: String?, priority: Int) = priority >= android.util.Log.INFO

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        android.util.Log.println(priority, tag ?: "Fukkatsu", if (t == null) message else "$message\n${android.util.Log.getStackTraceString(t)}")
    }
}
