package com.cyprienbrisset.fukkatsunop.ui.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cyprienbrisset.fukkatsunop.alarm.SunriseForegroundService
import com.cyprienbrisset.fukkatsunop.ui.theme.MyPortalTheme
import kotlinx.coroutines.delay

class SunriseActivity : ComponentActivity() {

    private val finishReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == SunriseForegroundService.ACTION_FINISH_ACTIVITY) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
        )
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, SunriseForegroundService.DURATION_MS)
        val startMs = System.currentTimeMillis()

        registerReceiver(finishReceiver, IntentFilter(SunriseForegroundService.ACTION_FINISH_ACTIVITY))

        setContent {
            var frac by remember { mutableFloatStateOf(0f) }
            LaunchedEffect(Unit) {
                while (frac < 1f) {
                    frac = ((System.currentTimeMillis() - startMs).toFloat() / durationMs).coerceIn(0f, 1f)
                    delay(1_000L)
                }
            }
            MyPortalTheme(darkTheme = true) {
                SunriseCanvas(frac = frac)
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(finishReceiver)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_DURATION_MS = "duration_ms"
    }
}
