package com.cyprienbrisset.myportal.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.myportal.overlay.OverlayService
import com.cyprienbrisset.myportal.system.DarkModeManager
import com.cyprienbrisset.myportal.ui.sumi.HankoSeal
import com.cyprienbrisset.myportal.ui.theme.Mincho
import com.cyprienbrisset.myportal.ui.theme.Shu

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onTiles: () -> Unit,
    onAlarms: () -> Unit,
    onWeather: () -> Unit,
    onStore: () -> Unit = {},
    onInstalledApps: () -> Unit = {},
    onDarkSchedule: () -> Unit = {},
) {
    val ctx = LocalContext.current
    var verifierDisabled by remember {
        mutableStateOf(
            runCatching {
                Settings.Global.getInt(ctx.contentResolver, "package_verifier_enable", 1) == 0
            }.getOrDefault(false)
        )
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 32.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            HankoSeal("朱", size = 40.dp, onClick = onBack)
            Spacer(Modifier.width(14.dp))
            Text("Réglages", fontFamily = Mincho, color = MaterialTheme.colorScheme.onBackground, fontSize = 22.sp)
        }
        SettingRow("Tuiles") { onTiles() }
        SettingRow("Alarmes") { onAlarms() }
        SettingRow("Ville météo") { onWeather() }
        SettingRow("FukkaStore") { onStore() }
        SettingRow("Applications installées") { onInstalledApps() }
        SettingRow("Mode nuit automatique") { onDarkSchedule() }
        SettingRow(
            text = if (verifierDisabled) "Vérificateur désactivé ✓" else "Activer FukkaStore (vérificateur)",
            chevron = !verifierDisabled,
        ) {
            if (!verifierDisabled) {
                runCatching {
                    Settings.Global.putInt(ctx.contentResolver, "package_verifier_enable", 0)
                    Settings.Global.putInt(ctx.contentResolver, "verifier_verify_adb_installs", 0)
                    Settings.Global.putInt(ctx.contentResolver, "package_verifier_user_consent", -1)
                    verifierDisabled = true
                }
            }
        }
        // Overlay
        var overlayRunning by remember { mutableStateOf(OverlayService.isRunning) }
        SettingRow(
            text = if (overlayRunning) "Contrôles flottants actifs ✓" else "Activer les contrôles flottants",
            chevron = !overlayRunning,
        ) {
            if (overlayRunning) {
                ctx.startService(Intent(ctx, OverlayService::class.java).apply { action = OverlayService.ACTION_STOP })
                overlayRunning = false
            } else {
                if (!Settings.canDrawOverlays(ctx)) {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else {
                    ctx.startForegroundService(Intent(ctx, OverlayService::class.java))
                    overlayRunning = true
                }
            }
        }

        SettingRow("Réglages système") {
            ctx.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}

@Composable
private fun SettingRow(text: String, chevron: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp).clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        if (chevron) Text("›", color = Shu, fontSize = 22.sp)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
}
