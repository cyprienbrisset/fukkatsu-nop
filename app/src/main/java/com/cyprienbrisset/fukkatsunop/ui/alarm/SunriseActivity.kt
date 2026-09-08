package com.cyprienbrisset.fukkatsunop.ui.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
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
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, SunriseForegroundService.DURATION_MS)
        val startMs = System.currentTimeMillis()

        registerReceiver(finishReceiver, IntentFilter(SunriseForegroundService.ACTION_FINISH_ACTIVITY))

        val videoResId = resources.getIdentifier("sunrise", "raw", packageName)

        setContent {
            MyPortalTheme(darkTheme = true) {
                var frac by remember { mutableFloatStateOf(0f) }
                LaunchedEffect(Unit) {
                    while (frac < 1f) {
                        frac = ((System.currentTimeMillis() - startMs).toFloat() / durationMs)
                            .coerceIn(0f, 1f)
                        delay(5_000L)
                    }
                }

                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    if (videoResId != 0) {
                        // TextureView pour que Compose puisse superposer le voile noir par-dessus
                        AndroidView(
                            factory = { ctx ->
                                TextureView(ctx).also { tv ->
                                    tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                        private var mp: MediaPlayer? = null

                                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                            mp = MediaPlayer().apply {
                                                setSurface(Surface(st))
                                                val afd = ctx.resources.openRawResourceFd(videoResId)
                                                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                                afd.close()
                                                isLooping = true
                                                setVolume(0f, 0f)
                                                prepare()
                                                start()
                                            }
                                        }

                                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                            mp?.release(); mp = null
                                            return true
                                        }

                                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        SunriseCanvas(frac = frac)
                    }

                    // Voile noir progressif : opaque au départ, transparent à l'alarme
                    val veilAlpha by animateFloatAsState(
                        targetValue = (1f - frac) * 0.88f,
                        animationSpec = tween(durationMillis = 5_000),
                        label = "sunrise_veil",
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = veilAlpha))
                    )
                }
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
