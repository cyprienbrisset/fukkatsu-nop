package com.cyprienbrisset.fukkatsunop.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyprienbrisset.fukkatsunop.ui.home.HomeViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.fukkatsunop.airplay.AirPlayPrefs
import com.cyprienbrisset.fukkatsunop.BuildConfig
import com.cyprienbrisset.fukkatsunop.alarm.SunriseForegroundService
import com.cyprienbrisset.fukkatsunop.overlay.OverlayService
import com.cyprienbrisset.fukkatsunop.ui.alarm.SunriseActivity
import com.cyprienbrisset.fukkatsunop.system.DarkModeManager
import com.cyprienbrisset.fukkatsunop.system.FirmwareWatcher
import com.cyprienbrisset.fukkatsunop.system.UpdateChecker
import com.cyprienbrisset.fukkatsunop.system.UpdateProgress
import com.cyprienbrisset.fukkatsunop.system.voice.VoiceModelManager
import com.cyprienbrisset.fukkatsunop.system.voice.VoiceService
import com.cyprienbrisset.fukkatsunop.ui.sumi.HankoSeal
import com.cyprienbrisset.fukkatsunop.ui.sumi.SumiPrimaryButton
import com.cyprienbrisset.fukkatsunop.ui.theme.AccentShu
import com.cyprienbrisset.fukkatsunop.ui.theme.Kinari
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiMuted
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val homeVm: HomeViewModel = viewModel()
    val weatherEffects by homeVm.weatherEffectsEnabled.collectAsState()
    val saverMode by homeVm.saverMode.collectAsState()
    var voiceEnabled by remember { mutableStateOf(VoiceService.isEnabled(ctx)) }
    var modelReady by remember { mutableStateOf(VoiceModelManager.isModelReady(ctx)) }
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var verifierDisabled by remember {
        mutableStateOf(
            runCatching {
                Settings.Global.getInt(ctx.contentResolver, "package_verifier_enable", 1) == 0
            }.getOrDefault(false)
        )
    }
    var updateAvailable by remember { mutableStateOf(UpdateChecker.availableVersionName(ctx)) }
    LaunchedEffect(Unit) {
        UpdateChecker.check(ctx)
        updateAvailable = UpdateChecker.availableVersionName(ctx)
    }
    var airPlayName by remember { mutableStateOf(AirPlayPrefs.getName(ctx)) }
    var showAirPlayNameDialog by remember { mutableStateOf(false) }
    var editingAirPlayName by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 32.dp)) {
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

        SettingRow("Nom AirPlay : $airPlayName", subtitle = "Visible sur Mac dans Réglages Système → Écrans") {
            editingAirPlayName = airPlayName
            showAirPlayNameDialog = true
        }
        if (showAirPlayNameDialog) {
            AlertDialog(
                onDismissRequest = { showAirPlayNameDialog = false },
                modifier = Modifier.fillMaxWidth(0.6f),
                title = { Text("Nom AirPlay", fontFamily = Mincho) },
                text = {
                    OutlinedTextField(
                        value = editingAirPlayName,
                        onValueChange = { editingAirPlayName = it },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val n = editingAirPlayName.trim().ifBlank { "Portal" }
                        AirPlayPrefs.setName(ctx, n)
                        airPlayName = n
                        showAirPlayNameDialog = false
                    }) { Text("OK", color = Shu) }
                },
                dismissButton = {
                    TextButton(onClick = { showAirPlayNameDialog = false }) { Text("Annuler") }
                },
                containerColor = MaterialTheme.colorScheme.surface,
            )
        }
        SettingRow("Surveillance firmware", subtitle = FirmwareWatcher.currentBuild(), chevron = false) {}
        val updatePct by UpdateProgress.pct.collectAsState()
        if (updateAvailable != null) {
            val busy = updatePct in 0..100
            val rowText = when {
                updatePct in 0..99 -> "Téléchargement… $updatePct%"
                updatePct == 100   -> "Installation en cours…"
                else               -> "Mise à jour disponible : $updateAvailable"
            }
            SettingRow(
                text = rowText,
                subtitle = if (busy) "Ne pas quitter l'application"
                           else "Version installée : ${BuildConfig.VERSION_NAME} — Appuyer pour installer",
                chevron = !busy,
            ) {
                if (!busy) UpdateChecker.startUpdate(ctx)
            }
        } else {
            SettingRow(
                text = "Version ${BuildConfig.VERSION_NAME}",
                subtitle = "À jour",
                chevron = false,
            ) {}
        }
        SettingRow("Réglages système") {
            ctx.startActivity(Intent(Settings.ACTION_SETTINGS))
        }

        // ── Commandes vocales ────────────────────────────────────────────────────
        if (!modelReady) {
            // Download prompt
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Commandes vocales (modèle ~40 MB)",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 17.sp,
                    )
                    Text(
                        if (downloading) "Téléchargement… $downloadProgress%" else "Requis pour la reconnaissance hors-ligne",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontFamily = Mincho,
                    )
                }
                Spacer(Modifier.width(10.dp))
                if (!downloading) {
                    Box(
                        Modifier
                            .clickable {
                                downloading = true
                                scope.launch {
                                    val ok = VoiceModelManager.downloadAndExtract(ctx) { p -> downloadProgress = p }
                                    modelReady = ok
                                    downloading = false
                                }
                            }
                    ) {
                        Text("Télécharger", color = Shu, fontSize = 15.sp, fontFamily = Mincho)
                    }
                } else {
                    CircularProgressIndicator(
                        color = AccentShu,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
        } else {
            // Toggle
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Commandes vocales",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 17.sp,
                    )
                    Text(
                        if (voiceEnabled) "Actif — dites « Portal » pour parler" else "Désactivé",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontFamily = Mincho,
                    )
                }
                Switch(
                    checked = voiceEnabled,
                    onCheckedChange = { enabled ->
                        voiceEnabled = enabled
                        VoiceService.setEnabled(ctx, enabled)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AccentShu,
                        checkedTrackColor = AccentShu.copy(alpha = 0.4f),
                    ),
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
        }

        // ── Effets météo ─────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().heightIn(min = 68.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Effets météo", color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp)
                Text(
                    if (weatherEffects) "Pluie, neige, brouillard animés sur l'écran d'accueil"
                    else "Désactivés",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontFamily = Mincho,
                )
            }
            Switch(
                checked = weatherEffects,
                onCheckedChange = { homeVm.setWeatherEffectsEnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AccentShu,
                    checkedTrackColor = AccentShu.copy(alpha = 0.4f),
                ),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))

        // ── Lever de soleil ──────────────────────────────────────────────────────
        SettingRow("Tester le lever de soleil (60 s)") {
            ctx.startActivity(
                android.content.Intent(ctx, SunriseActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(SunriseActivity.EXTRA_DURATION_MS, 60_000L)
            )
        }

        // ── Écran de veille Sumi-e ───────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().heightIn(min = 68.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Écran de veille Sumi-e", color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp)
                Text(
                    if (saverMode) "Bouton veille → encre de Chine générative" else "Bouton veille → verrouille l'écran",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontFamily = Mincho,
                )
            }
            Switch(
                checked = saverMode,
                onCheckedChange = { homeVm.setSaverMode(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AccentShu,
                    checkedTrackColor = AccentShu.copy(alpha = 0.4f),
                ),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
    }
}


@Composable
private fun SettingRow(text: String, subtitle: String? = null, chevron: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp).clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text, color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp)
            if (subtitle != null) {
                Text(subtitle, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 12.sp, fontFamily = Mincho)
            }
        }
        Spacer(Modifier.width(10.dp))
        if (chevron) Text("›", color = Shu, fontSize = 22.sp)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
}
