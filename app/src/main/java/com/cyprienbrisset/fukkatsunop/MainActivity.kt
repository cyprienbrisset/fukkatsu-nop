package com.cyprienbrisset.fukkatsunop

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.cyprienbrisset.fukkatsunop.alarm.AlarmForegroundService
import com.cyprienbrisset.fukkatsunop.alarm.AlarmRingActivity
import com.cyprienbrisset.fukkatsunop.alarm.AlarmReceiver
import com.cyprienbrisset.fukkatsunop.integration.google.ChatAuthManager
import androidx.lifecycle.lifecycleScope
import com.cyprienbrisset.fukkatsunop.data.settings.SettingsRepository
import com.cyprienbrisset.fukkatsunop.system.DarkModeManager
import com.cyprienbrisset.fukkatsunop.ui.theme.applyAccentOverride
import com.cyprienbrisset.fukkatsunop.ui.theme.applyBgTone
import com.cyprienbrisset.fukkatsunop.ui.theme.applyIconShape
import kotlinx.coroutines.launch
import com.cyprienbrisset.fukkatsunop.overlay.OverlayPrefs
import com.cyprienbrisset.fukkatsunop.overlay.OverlayService
import com.cyprienbrisset.fukkatsunop.system.FirmwareWatcher
import com.cyprienbrisset.fukkatsunop.system.UpdateChecker
import com.cyprienbrisset.fukkatsunop.system.voice.VoiceService
import com.cyprienbrisset.fukkatsunop.ui.AppNav
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho
import com.cyprienbrisset.fukkatsunop.ui.theme.MyPortalTheme
import com.cyprienbrisset.fukkatsunop.ui.theme.AccentShu
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu
import kotlinx.coroutines.delay

// Cubic ease-out for natural deceleration
private val EaseOut = Easing { t -> 1f - (1f - t) * (1f - t) * (1f - t) }

class MainActivity : ComponentActivity() {
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // Incrémenté à chaque appui sur Home (onNewIntent) pour que AppNav revienne à l'accueil.
    companion object {
        private val _goHome = MutableStateFlow(0)
        val goHome: StateFlow<Int> = _goHome

        // Relais d'alarme : AlarmReceiver y dépose l'ID quand startActivity direct échoue
        // à amener AlarmRingActivity au premier plan (Portal 1). MainActivity lance alors
        // l'activité depuis son propre contexte, ce qui fonctionne sans FLAG_ACTIVITY_NEW_TASK.
        data class PendingAlarm(val alarmId: Long, val videoEnabled: Boolean)
        private val _pendingAlarm = MutableStateFlow<PendingAlarm?>(null)
        val pendingAlarm: StateFlow<PendingAlarm?> = _pendingAlarm

        fun triggerAlarm(alarmId: Long, videoEnabled: Boolean) {
            _pendingAlarm.value = PendingAlarm(alarmId, videoEnabled)
        }
        fun clearAlarm() { _pendingAlarm.value = null }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val code = intent.data?.getQueryParameter("code")
        if (code != null) {
            ChatAuthManager.onAuthCode(code)
        } else {
            _goHome.value++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Keep system splash on screen until our Compose animation takes over
        var splashReady = false
        splashScreen.setKeepOnScreenCondition { !splashReady }

        startService(Intent(this, com.cyprienbrisset.fukkatsunop.airplay.AirPlayService::class.java))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        DarkModeManager.initFromSystem(this)
        OverlayPrefs.init(this)
        val repo = SettingsRepository(this)
        lifecycleScope.launch { repo.accentOverride.collect { applyAccentOverride(it) } }
        lifecycleScope.launch { repo.bgTone.collect { applyBgTone(it) } }
        lifecycleScope.launch { repo.iconShape.collect { applyIconShape(it) } }
        FirmwareWatcher.init(this)
        lifecycleScope.launch { UpdateChecker.check(this@MainActivity) }

        // Request RECORD_AUDIO if voice enabled
        if (VoiceService.isEnabled(this)) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                VoiceService.start(this)
            } else {
                requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 42)
            }
        }

        enableEdgeToEdge()

        setContent {
            val isDark by DarkModeManager.isDarkFlow.collectAsState()
            MyPortalTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    var showSplash by remember { mutableStateOf(true) }

                    // Release the system splash once our Compose tree is drawn
                    LaunchedEffect(Unit) {
                        splashReady = true
                    }

                    // Relais alarme Portal 1 : démarre AlarmRingActivity depuis un contexte
                    // Activity (sans FLAG_ACTIVITY_NEW_TASK) pour qu'elle passe au premier plan.
                    val alarm by pendingAlarm.collectAsState()
                    LaunchedEffect(alarm) {
                        val a = alarm ?: return@LaunchedEffect
                        clearAlarm()
                        startActivity(
                            Intent(this@MainActivity, AlarmRingActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                                .putExtra(AlarmReceiver.EXTRA_ALARM_ID, a.alarmId)
                                .putExtra(AlarmForegroundService.EXTRA_VIDEO_ENABLED, a.videoEnabled)
                        )
                    }

                    Box(Modifier.fillMaxSize()) {
                        AppNav()
                        if (showSplash) {
                            SplashOverlay(onDone = { showSplash = false })
                        }
                    }
                }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        if (android.provider.Settings.canDrawOverlays(this) && !OverlayService.isRunning) {
            startService(Intent(this, OverlayService::class.java))
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // Prevent back from traversing out of the launcher into previously-visited apps.
        moveTaskToBack(false)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 42 && grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            VoiceService.start(this)
        }
    }
}

@Composable
private fun SplashOverlay(onDone: () -> Unit) {
    // Phase 0 → 1: fade-in + scale up (0–1200ms)
    // Phase 1: hold (1200–2800ms)
    // Phase 2: fade out (2800–3700ms)
    var phase by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        delay(1200)
        phase = 1
        delay(1600)
        phase = 2
        delay(900)
        onDone()
    }

    val alpha by animateFloatAsState(
        targetValue = if (phase < 2) 1f else 0f,
        animationSpec = tween(durationMillis = if (phase < 2) 1200 else 800, easing = EaseOut),
        label = "splash-alpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (phase == 0) 0.88f else if (phase == 1) 1f else 1.05f,
        animationSpec = tween(durationMillis = 1200, easing = EaseOut),
        label = "splash-scale",
    )

    Box(
        Modifier
            .fillMaxSize()
            .alpha(alpha)
            .background(Color(0xFF0D0E12)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "復活",
                fontFamily = Mincho,
                fontWeight = FontWeight.Medium,
                color = AccentShu,
                fontSize = 128.sp,
                modifier = Modifier.scale(scale),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "F U K K A T S U   N O   P",
                color = Color(0xFFECE7DD).copy(alpha = 0.55f),
                fontSize = 11.sp,
                letterSpacing = 7.sp,
                fontFamily = Mincho,
            )
        }
    }
}
