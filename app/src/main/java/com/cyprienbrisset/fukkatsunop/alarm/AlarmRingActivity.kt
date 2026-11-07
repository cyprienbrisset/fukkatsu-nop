package com.cyprienbrisset.fukkatsunop.alarm

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiColors
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiPhoto
import com.cyprienbrisset.fukkatsunop.ui.fuji.FitToPortal
import com.cyprienbrisset.fukkatsunop.ui.fuji.Moment
import com.cyprienbrisset.fukkatsunop.ui.theme.Gothic
import java.time.LocalDate
import java.util.Locale

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.view.Surface
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cyprienbrisset.fukkatsunop.data.AppDatabase
import com.cyprienbrisset.fukkatsunop.data.alarm.AlarmRepository
import com.cyprienbrisset.fukkatsunop.ui.sumi.SumiChoiceChip
import com.cyprienbrisset.fukkatsunop.ui.theme.Kinari
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho
import com.cyprienbrisset.fukkatsunop.ui.theme.MyPortalTheme
import com.cyprienbrisset.fukkatsunop.ui.theme.AccentShu
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class AlarmRingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        val alarmId = intent.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, -1)
        val videoEnabled = intent.getBooleanExtra(AlarmForegroundService.EXTRA_VIDEO_ENABLED, false)
        val videoResId = if (videoEnabled) resources.getIdentifier("sunrise", "raw", packageName) else 0

        val startMs = System.currentTimeMillis()
        val veilDurationMs = 5 * 60 * 1000L  // voile s'efface sur 5 minutes

        setContent {
            var snoozeMinutes by remember { mutableStateOf(10) }
            if (alarmId >= 0) {
                LaunchedEffect(alarmId) {
                    val a = withContext(Dispatchers.IO) {
                        AlarmRepository(AppDatabase.get(this@AlarmRingActivity).alarmDao()).byId(alarmId)
                    }
                    if (a != null) snoozeMinutes = a.snoozeMinutes
                }
            }

            // frac : 0 au départ → 1 après veilDurationMs (le voile s'efface)
            var frac by remember { mutableFloatStateOf(0f) }
            if (videoResId != 0) {
                LaunchedEffect(Unit) {
                    while (frac < 1f) {
                        frac = ((System.currentTimeMillis() - startMs).toFloat() / veilDurationMs).coerceIn(0f, 1f)
                        delay(5_000L)
                    }
                }
            }
            val veilAlpha by animateFloatAsState(
                targetValue = if (videoResId != 0) (1f - frac) * 0.92f else 0f,
                animationSpec = tween(durationMillis = 5_000),
                label = "veil",
            )

            MyPortalTheme(darkTheme = true) { FitToPortal {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    if (videoResId != 0) {
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
                                                isLooping = true; setVolume(0f, 0f); prepare(); start()
                                            }
                                        }
                                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                            mp?.release(); mp = null; return true
                                        }
                                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        // Voile qui s'efface progressivement sur 5 minutes
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = veilAlpha)))

                    } else {
                        FujiPhoto(Moment.AUBE, Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Color(0x40080A12)))
                    }

                    Box(Modifier.fillMaxSize()) {
                        val shadow = TextStyle(shadow = Shadow(Color(0x80000000), blurRadius = 28f))
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 110.dp),
                        ) {
                            Text("RÉVEIL", color = Color.White, fontFamily = Gothic, fontSize = 30.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp, style = shadow)
                            Text(
                                LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                                color = Color.White, fontFamily = Gothic, fontSize = 300.sp, fontWeight = FontWeight.Light,
                                letterSpacing = (-8).sp, lineHeight = 300.sp, style = shadow,
                            )
                            Text(
                                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)).replaceFirstChar { it.titlecase(Locale.FRENCH) },
                                color = Color.White, fontFamily = Gothic, fontSize = 32.sp, style = shadow,
                            )
                        }
                        Row(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                        ) {
                            RingButton("Snooze", "$snoozeMinutes minutes de plus", primary = false) {
                                if (alarmId >= 0) AlarmForegroundService.snooze(this@AlarmRingActivity, alarmId, snoozeMinutes)
                                finish()
                            }
                            RingButton("Arrêter", "Bonne journée", primary = true) {
                                AlarmForegroundService.stop(this@AlarmRingActivity); finish()
                            }
                        }
                    }
                }
            } }
        }
    }

    @Composable
    private fun RingButton(title: String, sub: String, primary: Boolean, onClick: () -> Unit) {
        Column(
            Modifier
                .size(width = 560.dp, height = 150.dp)
                .clip(RoundedCornerShape(75.dp))
                .background(if (primary) FujiColors.Cream else FujiColors.GlassDark)
                .clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val fg = if (primary) FujiColors.Ink else Color.White
            Text(title, color = fg, fontFamily = Gothic, fontSize = 36.sp, fontWeight = if (primary) FontWeight.Bold else FontWeight.Medium)
            Text(sub, color = fg.copy(alpha = 0.75f), fontFamily = Gothic, fontSize = 22.sp)
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }
}
