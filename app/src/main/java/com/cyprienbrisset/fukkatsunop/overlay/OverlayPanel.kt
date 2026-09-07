package com.cyprienbrisset.fukkatsunop.overlay

import android.media.AudioManager
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.fukkatsunop.system.DarkModeManager
import com.cyprienbrisset.fukkatsunop.ui.theme.Kinari
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu

private val BG_PANEL = Color(0xF2131313)
private val BG_TAB = Color(0xEE1C1C1C)
private val TRACK_ACTIVE = Shu
private val TRACK_INACTIVE = Color(0xFF2E2E2E)

@Composable
fun OverlayPanel(
    windowManager: WindowManager,
    overlayView: () -> View,
    overlayParams: WindowManager.LayoutParams,
    onStop: () -> Unit,
) {
    val ctx = LocalContext.current
    val audioManager = remember { ctx.getSystemService(AudioManager::class.java) }
    val maxVol = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    val canWrite = remember { Settings.System.canWrite(ctx) }

    var expanded by remember { mutableStateOf(false) }
    var isDark by remember { mutableStateOf(DarkModeManager.isDark(ctx)) }
    var volume by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var brightness by remember {
        mutableFloatStateOf(
            runCatching { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS).toFloat() }
                .getOrDefault(128f)
        )
    }

    // Resync sliders each time the panel opens
    LaunchedEffect(expanded) {
        if (expanded) {
            volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
            brightness = runCatching {
                Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS).toFloat()
            }.getOrDefault(128f)
            isDark = DarkModeManager.isDark(ctx)
        }
    }

    // Wrap in MaterialTheme so Compose Material3 components work outside an Activity
    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            primary = Shu,
            surface = BG_PANEL,
            onSurface = Kinari,
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // ── Control panel ────────────────────────────────────────────────
            AnimatedVisibility(
                visible = expanded,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            ) {
                Column(
                    Modifier
                        .width(260.dp)
                        .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                        .background(BG_PANEL)
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("Contrôles", color = Kinari.copy(alpha = 0.4f), fontFamily = Mincho, fontSize = 10.sp, fontWeight = FontWeight.Medium)

                    // Brightness
                    ControlSlider(
                        icon = { Icon(Icons.Rounded.WbSunny, null, tint = Shu, modifier = Modifier.size(18.dp)) },
                        label = "Luminosité",
                        value = brightness / 255f,
                        enabled = canWrite,
                        hint = if (!canWrite) "Autorisation requise" else null,
                        onValueChange = { f ->
                            brightness = (f * 255).coerceIn(5f, 255f)
                            if (canWrite) {
                                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness.toInt())
                            }
                        },
                    )

                    // Volume
                    ControlSlider(
                        icon = { Icon(Icons.Rounded.VolumeUp, null, tint = Shu, modifier = Modifier.size(18.dp)) },
                        label = "Volume",
                        value = volume / maxVol,
                        onValueChange = { f ->
                            volume = (f * maxVol).coerceIn(0f, maxVol.toFloat())
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume.toInt(), 0)
                        },
                    )

                    Spacer(Modifier.height(2.dp))

                    // Action buttons
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Dark/light toggle
                        ActionButton(
                            icon = {
                                Icon(
                                    if (isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                    null,
                                    tint = if (isDark) Color(0xFFFFCC00) else Color(0xFF8A8AFF),
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            label = if (isDark) "Jour" else "Nuit",
                            modifier = Modifier.weight(1f),
                        ) {
                            val next = !isDark
                            DarkModeManager.apply(ctx, next)
                            isDark = next
                        }
                        // Home
                        ActionButton(
                            icon = { Icon(Icons.Rounded.Home, null, tint = Kinari, modifier = Modifier.size(18.dp)) },
                            label = "Accueil",
                            modifier = Modifier.weight(1f),
                        ) {
                            ctx.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                                    addCategory(android.content.Intent.CATEGORY_HOME)
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        // Stop overlay
                        ActionButton(
                            icon = { Icon(Icons.Rounded.PowerSettingsNew, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(18.dp)) },
                            label = "Fermer l'overlay",
                            modifier = Modifier.weight(1f),
                        ) { onStop() }
                    }
                }
            }

            // ── Tab handle ───────────────────────────────────────────────────
            Box(
                Modifier
                    .width(40.dp)
                    .height(96.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                    .background(BG_TAB)
                    .clickable { expanded = !expanded }
                    .pointerInput(Unit) {
                        detectDragGestures { _, dragAmount ->
                            overlayParams.y += dragAmount.y.toInt()
                            runCatching { windowManager.updateViewLayout(overlayView(), overlayParams) }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        if (expanded) Icons.Rounded.ChevronRight else Icons.Rounded.ChevronLeft,
                        contentDescription = null,
                        tint = Kinari.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp),
                    )
                    Icon(Icons.Rounded.WbSunny, contentDescription = null, tint = Kinari.copy(alpha = 0.3f), modifier = Modifier.size(12.dp))
                    Icon(Icons.Rounded.VolumeUp, contentDescription = null, tint = Kinari.copy(alpha = 0.3f), modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

@Composable
private fun ControlSlider(
    icon: @Composable () -> Unit,
    label: String,
    value: Float,
    enabled: Boolean = true,
    hint: String? = null,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            icon()
            Text(label, color = Kinari.copy(alpha = if (enabled) 0.85f else 0.4f), fontFamily = Mincho, fontSize = 12.sp)
        }
        if (hint != null) {
            Text(hint, color = Color(0xFFFF9800), fontFamily = Mincho, fontSize = 10.sp)
        } else {
            Slider(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(28.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Shu,
                    activeTrackColor = TRACK_ACTIVE,
                    inactiveTrackColor = TRACK_INACTIVE,
                ),
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon: @Composable () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1E1E1E))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon()
        Text(label, color = Kinari.copy(alpha = 0.75f), fontFamily = Mincho, fontSize = 11.sp, maxLines = 1)
    }
}
