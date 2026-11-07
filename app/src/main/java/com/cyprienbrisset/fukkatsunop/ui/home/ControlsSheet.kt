package com.cyprienbrisset.fukkatsunop.ui.home

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.fukkatsunop.airplay.AirPlayReceiver
import com.cyprienbrisset.fukkatsunop.airplay.AirPlayService
import com.cyprienbrisset.fukkatsunop.airplay.AirPlayState
import com.cyprienbrisset.fukkatsunop.system.DarkModeManager
import com.cyprienbrisset.fukkatsunop.system.DndController
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiButton
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiColors
import com.cyprienbrisset.fukkatsunop.ui.theme.Gothic
import kotlinx.coroutines.delay

/** Pull-down control centre: big round toggles, two thick sliders, screen actions. */
@Composable
fun ControlsSheet(
    onClose: () -> Unit,
    onDnd: () -> Unit,
    onScreenSaver: () -> Unit,
    onScreenOff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val wifi = remember { ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }
    val bt = remember { (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter }
    val audio = remember { ctx.getSystemService(AudioManager::class.java) }
    val maxVol = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    val canWrite = remember { Settings.System.canWrite(ctx) }
    val isDark by DarkModeManager.isDarkFlow.collectAsState()
    val airPlay by AirPlayReceiver.state.collectAsState()
    val macVisible by AirPlayReceiver.advertising.collectAsState()

    var wifiOn by remember { mutableStateOf(wifi.isWifiEnabled) }
    var btOn by remember { mutableStateOf(bt?.isEnabled == true) }
    var dndOn by remember { mutableStateOf(DndController.isGranted(ctx) && DndController.isDndOn(ctx)) }
    var autoLight by remember { mutableStateOf(isAutoBrightness(ctx)) }
    var brightness by remember { mutableFloatStateOf(readBrightness(ctx)) }
    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol) }

    // Resync with changes made elsewhere (hardware keys, Android settings, DND timer).
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            wifiOn = wifi.isWifiEnabled
            btOn = bt?.isEnabled == true
            dndOn = DndController.isGranted(ctx) && DndController.isDndOn(ctx)
            autoLight = isAutoBrightness(ctx)
        }
    }

    @Suppress("DEPRECATION")
    val ssid = remember(wifiOn) {
        if (wifiOn) wifi.connectionInfo?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" } else null
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 64.dp)
            .clip(RoundedCornerShape(bottomStart = 44.dp, bottomEnd = 44.dp))
            .background(FujiColors.GlassDark.copy(alpha = 0.86f))
            // Swallow taps so they don't reach the scrim that closes the sheet.
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .padding(start = 64.dp, end = 64.dp, top = 48.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RoundToggle(Icons.Rounded.Wifi, "Wi-Fi", if (wifiOn) ssid ?: "Activé" else "Désactivé", wifiOn) {
                @Suppress("DEPRECATION")
                wifi.setWifiEnabled(!wifiOn)
                wifiOn = !wifiOn
            }
            RoundToggle(Icons.Rounded.Bluetooth, "Bluetooth", if (btOn) "Activé" else "Désactivé", btOn) {
                @Suppress("DEPRECATION", "MissingPermission")
                runCatching { if (btOn) bt?.disable() else bt?.enable() }
                btOn = !btOn
            }
            RoundToggle(Icons.Rounded.DoNotDisturbOn, "Ne pas déranger", if (dndOn) "Activé" else "Désactivé", dndOn) {
                when {
                    !DndController.isGranted(ctx) -> DndController.toggleOrRequest(ctx)
                    dndOn -> { DndController.disable(ctx); dndOn = false }
                    else -> onDnd()
                }
            }
            RoundToggle(Icons.Rounded.DarkMode, "Mode nuit", if (isDark) "Activé" else "Désactivé", isDark) {
                DarkModeManager.apply(ctx, !isDark)
            }
            RoundToggle(Icons.Rounded.Laptop, "Écran Mac", airPlayLabel(airPlay, macVisible), macVisible) {
                val svc = Intent(ctx, AirPlayService::class.java)
                if (macVisible) ctx.stopService(svc) else ctx.startForegroundService(svc)
            }
            RoundToggle(Icons.Rounded.BrightnessAuto, "Lumi. auto", if (autoLight) "Activée" else "Désactivée", autoLight, enabled = canWrite) {
                setAutoBrightness(ctx, !autoLight)
                autoLight = !autoLight
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            FujiSlider(
                label = "Luminosité",
                icon = Icons.Rounded.WbSunny,
                value = brightness,
                enabled = canWrite,
                hint = if (canWrite) null else "Autorisation requise",
                modifier = Modifier.weight(1f),
            ) { f ->
                brightness = f
                setBrightness(ctx, f)
                autoLight = false
            }
            FujiSlider(
                label = "Volume",
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                value = volume,
                modifier = Modifier.weight(1f),
            ) { f ->
                volume = f
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, (f * maxVol).toInt(), 0)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FujiButton("Économiseur d'écran", onScreenSaver, Modifier.weight(1f), icon = Icons.Rounded.Tv, height = 96.dp)
            FujiButton("Éteindre l'écran", onScreenOff, Modifier.weight(1f), primary = true, icon = Icons.Rounded.PowerSettingsNew, height = 96.dp)
        }

        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 240.dp, height = 48.dp)
                .clickable(role = Role.Button, onClickLabel = "Fermer", onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = 120.dp, height = 8.dp).clip(CircleShape).background(FujiColors.Faint))
        }
    }
}

private fun airPlayLabel(state: AirPlayState, visible: Boolean): String = when {
    !visible -> "Masqué"
    state is AirPlayState.Streaming -> "Connecté"
    state is AirPlayState.Error -> "Erreur"
    else -> "Visible"
}

@Composable
private fun RoundToggle(
    icon: ImageVector,
    label: String,
    state: String,
    on: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .width(200.dp)
            .clickable(enabled = enabled, role = Role.Switch, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(132.dp)
                .clip(CircleShape)
                .background(if (on) FujiColors.Cream else FujiColors.GlassStrong),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (on) FujiColors.Ink else FujiColors.Cream, modifier = Modifier.size(54.dp))
        }
        Text(label, color = FujiColors.Cream.copy(alpha = if (enabled) 1f else 0.5f), fontFamily = Gothic, fontSize = 23.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(state, color = FujiColors.Muted, fontFamily = Gothic, fontSize = 19.sp, maxLines = 1)
    }
}

/** Thick 104dp slider: tap or drag anywhere on the track. */
@Composable
fun FujiSlider(
    label: String,
    icon: ImageVector,
    value: Float,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hint: String? = null,
    onChange: (Float) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = FujiColors.Cream, fontFamily = Gothic, fontSize = 23.sp, fontWeight = FontWeight.Medium)
            Text(hint ?: "${(value * 100).toInt()} %", color = FujiColors.Muted, fontFamily = Gothic, fontSize = 23.sp)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(104.dp)
                .clip(RoundedCornerShape(52.dp))
                .background(FujiColors.GlassStrong)
                .then(
                    if (!enabled) Modifier else Modifier
                        .pointerInput(Unit) {
                            detectTapGestures { o -> onChange((o.x / size.width).coerceIn(0.02f, 1f)) }
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { change, _ ->
                                onChange((change.position.x / size.width).coerceIn(0.02f, 1f))
                            }
                        },
                ),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(value.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(52.dp))
                    .background(if (enabled) FujiColors.Cream else FujiColors.Faint),
            )
            Icon(
                icon, null,
                tint = if (value > 0.12f) FujiColors.Ink else FujiColors.Cream,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 34.dp).size(36.dp),
            )
        }
    }
}

private fun isAutoBrightness(ctx: Context): Boolean =
    Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
        Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

private fun setAutoBrightness(ctx: Context, enabled: Boolean) {
    runCatching {
        Settings.System.putInt(
            ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            if (enabled) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
    }
}

private fun readBrightness(ctx: Context): Float =
    runCatching { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(0.5f)

private fun setBrightness(ctx: Context, f: Float) {
    if (!Settings.System.canWrite(ctx)) return
    runCatching {
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (f * 255).toInt().coerceIn(5, 255))
    }
}
