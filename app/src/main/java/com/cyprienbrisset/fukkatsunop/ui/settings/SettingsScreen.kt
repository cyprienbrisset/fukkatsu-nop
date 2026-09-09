package com.cyprienbrisset.fukkatsunop.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyprienbrisset.fukkatsunop.BuildConfig
import com.cyprienbrisset.fukkatsunop.airplay.AirPlayPrefs
import com.cyprienbrisset.fukkatsunop.overlay.OverlayService
import com.cyprienbrisset.fukkatsunop.system.FirmwareWatcher
import com.cyprienbrisset.fukkatsunop.system.UpdateChecker
import com.cyprienbrisset.fukkatsunop.system.UpdateProgress
import com.cyprienbrisset.fukkatsunop.system.voice.VoiceModelManager
import com.cyprienbrisset.fukkatsunop.system.voice.VoiceService
import com.cyprienbrisset.fukkatsunop.ui.alarm.SunriseActivity
import com.cyprienbrisset.fukkatsunop.ui.screensaver.SumiSaverActivity
import com.cyprienbrisset.fukkatsunop.ui.home.HomeViewModel
import com.cyprienbrisset.fukkatsunop.ui.sumi.HankoSeal
import com.cyprienbrisset.fukkatsunop.data.settings.SettingsRepository as Repo
import com.cyprienbrisset.fukkatsunop.ui.theme.AccentShu
import com.cyprienbrisset.fukkatsunop.ui.theme.Kinari
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho
import com.cyprienbrisset.fukkatsunop.ui.theme.Momiji
import com.cyprienbrisset.fukkatsunop.ui.theme.Sakura
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiMuted
import kotlinx.coroutines.launch

// ── Catégories ───────────────────────────────────────────────────────────────

private enum class Cat { HOME, DISPLAY, APPS, SYSTEM, DEVICE }

private data class CatDef(val kanji: String, val label: String, val id: Cat)

private val CATS = listOf(
    CatDef("宅", "Écran d'accueil", Cat.HOME),
    CatDef("映", "Affichage",       Cat.DISPLAY),
    CatDef("庫", "Applications",    Cat.APPS),
    CatDef("系", "Système",         Cat.SYSTEM),
    CatDef("機", "Appareil",        Cat.DEVICE),
)

// ── SettingsScreen ────────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onTiles: () -> Unit,
    onWeather: () -> Unit,
    onStore: () -> Unit = {},
    onInstalledApps: () -> Unit = {},
    onDarkSchedule: () -> Unit = {},
) {
    val ctx    = LocalContext.current
    val scope  = rememberCoroutineScope()
    val homeVm: HomeViewModel = viewModel()

    val weatherEffects  by homeVm.weatherEffectsEnabled.collectAsState()
    val saverMode       by homeVm.saverMode.collectAsState()
    val presenceEnabled by homeVm.presenceEnabled.collectAsState()

    var voiceEnabled     by remember { mutableStateOf(VoiceService.isEnabled(ctx)) }
    var modelReady       by remember { mutableStateOf(VoiceModelManager.isModelReady(ctx)) }
    val voiceDownloadProgress by VoiceModelManager.downloadProgress.collectAsState()
    var overlayRunning   by remember { mutableStateOf(OverlayService.isRunning) }
    var verifierDisabled by remember {
        mutableStateOf(runCatching {
            Settings.Global.getInt(ctx.contentResolver, "package_verifier_enable", 1) == 0
        }.getOrDefault(false))
    }
    var updateAvailable by remember { mutableStateOf(UpdateChecker.availableVersionName(ctx)) }
    LaunchedEffect(Unit) { UpdateChecker.check(ctx); updateAvailable = UpdateChecker.availableVersionName(ctx) }

    var airPlayName       by remember { mutableStateOf(AirPlayPrefs.getName(ctx)) }
    var showAirPlayDialog by remember { mutableStateOf(false) }
    var editingAirPlay    by remember { mutableStateOf("") }

    var selected     by remember { mutableStateOf(Cat.HOME) }
    var showCredits  by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {

        // ── Two-panel layout ─────────────────────────────────────────────────
        Row(Modifier.fillMaxSize().statusBarsPadding()) {

            // ── Left panel — category list ───────────────────────────────────
            Column(
                Modifier
                    .width(240.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                // Header
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HankoSeal("朱", size = 36.dp, onClick = onBack)
                    Spacer(Modifier.width(12.dp))
                    Text("Réglages", fontFamily = Mincho, fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))

                CATS.forEach { cat ->
                    CatItem(cat, selected == cat.id) { selected = cat.id }
                }

                // Credits — séparé en bas
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
                CatItem(CatDef("礼", "Licences", Cat.HOME), false) { showCredits = true }
            }

            // ── Separator ────────────────────────────────────────────────────
            Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))

            // ── Right panel — content ────────────────────────────────────────
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (selected == Cat.HOME) {
                    // TileEditScreen a ses propres LazyVerticalGrid — embarqué directement.
                    TileEditScreen(onBack = {})
                } else if (selected == Cat.APPS) {
                    // InstalledAppsScreen a une LazyColumn — embarquée directement.
                    InstalledAppsScreen(onBack = {})
                } else {
                // Panel title
                val title = CATS.first { it.id == selected }.label
                Text(
                    title,
                    fontFamily = Mincho,
                    fontWeight = FontWeight.Medium,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 22.dp),
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))

                // Animated panel content
                AnimatedContent(
                    targetState = selected,
                    transitionSpec = {
                        (fadeIn() + slideInHorizontally { it / 6 }) togetherWith
                        (fadeOut() + slideOutHorizontally { -it / 6 })
                    },
                    label = "settings-panel",
                ) { cat ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 32.dp, vertical = 8.dp),
                    ) {
                        when (cat) {
                            Cat.HOME    -> {} // handled above
                            Cat.DISPLAY -> DisplayPanelContent(
                                weatherEffects, saverMode,
                                onWeatherEffects = { homeVm.setWeatherEffectsEnabled(it) },
                                onSaverMode      = { homeVm.setSaverMode(it) },
                                onWeather        = onWeather,
                                onDarkSchedule   = onDarkSchedule,
                                onSunrise        = {
                                    ctx.startActivity(
                                        Intent(ctx, SunriseActivity::class.java)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            .putExtra(SunriseActivity.EXTRA_DURATION_MS, 60_000L)
                                    )
                                },
                                onLaunchSaver    = {
                                    ctx.startActivity(
                                        Intent(ctx, SumiSaverActivity::class.java)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                },
                            )
                            Cat.APPS    -> {} // handled above (InstalledAppsScreen embedded)
                            Cat.SYSTEM  -> SystemPanelContent(
                                overlayRunning, airPlayName, voiceEnabled, modelReady, voiceDownloadProgress,
                                presenceEnabled = presenceEnabled,
                                onOverlay = {
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
                                },
                                onAirPlay = { editingAirPlay = airPlayName; showAirPlayDialog = true },
                                onVoiceToggle = { enabled ->
                                    voiceEnabled = enabled; VoiceService.setEnabled(ctx, enabled)
                                },
                                onVoiceDownload = {
                                    scope.launch {
                                        val ok = VoiceModelManager.downloadAndExtract(ctx)
                                        modelReady = ok
                                    }
                                },
                                onPresenceToggle = { homeVm.setPresenceEnabled(ctx, it) },
                            )
                            Cat.DEVICE  -> {
                                val updatePct by UpdateProgress.pct.collectAsState()
                                DevicePanelContent(
                                    verifierDisabled, updateAvailable, updatePct,
                                    onVerifier = {
                                        runCatching {
                                            Settings.Global.putInt(ctx.contentResolver, "package_verifier_enable", 0)
                                            Settings.Global.putInt(ctx.contentResolver, "verifier_verify_adb_installs", 0)
                                            Settings.Global.putInt(ctx.contentResolver, "package_verifier_user_consent", -1)
                                            verifierDisabled = true
                                        }
                                    },
                                    onUpdate = { UpdateChecker.startUpdate(ctx) },
                                    onSystem  = { ctx.startActivity(Intent(Settings.ACTION_SETTINGS)) },
                                )
                            }
                        }
                        Spacer(Modifier.height(32.dp))
                    }
                }
                } // end else (not Cat.HOME)
            }
        }

        // ── Credits plein écran ──────────────────────────────────────────────
        AnimatedVisibility(
            visible = showCredits,
            enter   = slideInHorizontally { it },
            exit    = slideOutHorizontally { it },
        ) {
            CreditsScreen(onBack = { showCredits = false })
        }
    }

    // ── AirPlay dialog ───────────────────────────────────────────────────────
    if (showAirPlayDialog) {
        AlertDialog(
            onDismissRequest = { showAirPlayDialog = false },
            modifier = Modifier.fillMaxWidth(0.5f),
            title = { Text("Nom AirPlay", fontFamily = Mincho) },
            text = {
                OutlinedTextField(
                    value = editingAirPlay,
                    onValueChange = { editingAirPlay = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = editingAirPlay.trim().ifBlank { "Portal" }
                    AirPlayPrefs.setName(ctx, n); airPlayName = n; showAirPlayDialog = false
                }) { Text("OK", color = Shu) }
            },
            dismissButton = { TextButton(onClick = { showAirPlayDialog = false }) { Text("Annuler") } },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

// ── Left panel item ───────────────────────────────────────────────────────────

@Composable
private fun CatItem(cat: CatDef, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(if (active) Shu.copy(alpha = 0.08f) else Color.Transparent)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(36.dp).background(if (active) Shu else Color.Transparent))
        Spacer(Modifier.width(16.dp))
        Text(cat.kanji, fontFamily = Mincho, fontSize = 18.sp, color = if (active) Shu else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
        Spacer(Modifier.width(14.dp))
        Text(cat.label, fontSize = 15.sp, color = if (active) Shu else MaterialTheme.colorScheme.onBackground)
    }
}

// ── Panel contents ────────────────────────────────────────────────────────────

@Composable
private fun DisplayPanelContent(
    weatherEffects: Boolean,
    saverMode: Boolean,
    onWeatherEffects: (Boolean) -> Unit,
    onSaverMode: (Boolean) -> Unit,
    onWeather: () -> Unit,
    onDarkSchedule: () -> Unit,
    onSunrise: () -> Unit,
    onLaunchSaver: () -> Unit = {},
) {
    val ctx2 = LocalContext.current
    val scope2 = rememberCoroutineScope()
    val settingsRepo2 = remember(ctx2) { Repo(ctx2) }
    val accentOverride by settingsRepo2.accentOverride.collectAsState(initial = "AUTO")

    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Couleur d'accent", color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp)
            Text(
                when (accentOverride) {
                    "SAKURA" -> "Rose sakura (printemps)"
                    "MOMIJI" -> "Momiji automnal"
                    "SHU"    -> "Shu vermillon"
                    else     -> "Automatique (saisonnier)"
                },
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                fontSize = 12.sp, fontFamily = Mincho,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "AUTO"   to AccentShu,
                "SAKURA" to Sakura,
                "MOMIJI" to Momiji,
                "SHU"    to Shu,
            ).forEach { (key, color) ->
                val selected = accentOverride == key
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = if (selected) 1f else 0.4f))
                        .then(
                            if (selected) Modifier.border(2.dp, Kinari, CircleShape)
                            else Modifier
                        )
                        .clickable { scope2.launch { settingsRepo2.setAccentOverride(key) } },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Text("✓", color = Kinari, fontSize = 12.sp)
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))

    SettingSwitch(
        text = "Effets météo",
        subtitle = if (weatherEffects) "Pluie, neige, brouillard animés" else "Désactivés",
        checked = weatherEffects,
        onCheckedChange = onWeatherEffects,
    )
    SettingRow("Ville météo", subtitle = "Définir la ville pour la météo") { onWeather() }
    SettingRow("Mode nuit automatique", subtitle = "Planifier le passage en mode sombre") { onDarkSchedule() }
    SettingSwitch(
        text = "Écran de veille Sumi-e",
        subtitle = if (saverMode) "Bouton veille → encre de Chine générative" else "Bouton veille → verrouille l'écran",
        checked = saverMode,
        onCheckedChange = onSaverMode,
    )
    SettingRow("Lancer l'économiseur maintenant", subtitle = "Aperçu de l'animation encre de Chine") { onLaunchSaver() }
    SettingRow("Tester le lever de soleil", subtitle = "Simulation 60 secondes") { onSunrise() }
}

@Composable
private fun SystemPanelContent(
    overlayRunning: Boolean,
    airPlayName: String,
    voiceEnabled: Boolean,
    modelReady: Boolean,
    voiceDownloadProgress: Int?,
    presenceEnabled: Boolean,
    onOverlay: () -> Unit,
    onAirPlay: () -> Unit,
    onVoiceToggle: (Boolean) -> Unit,
    onVoiceDownload: () -> Unit,
    onPresenceToggle: (Boolean) -> Unit,
) {
    SettingRow(
        text = if (overlayRunning) "Contrôles flottants actifs ✓" else "Contrôles flottants",
        subtitle = if (overlayRunning) "Appuyer pour désactiver" else "Boutons multitâche et volume en superposition",
        chevron = !overlayRunning,
        onClick = onOverlay,
    )
    SettingRow("Nom AirPlay", subtitle = airPlayName, onClick = onAirPlay)

    // Commandes vocales
    val isDownloading = voiceDownloadProgress != null && voiceDownloadProgress != -1
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 68.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (modelReady) "Commandes vocales" else "Commandes vocales (modèle ~40 MB)",
                    color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp,
                )
                Text(
                    when {
                        voiceDownloadProgress == -1 -> "Erreur de téléchargement — réessayez"
                        voiceDownloadProgress == 100 -> "Extraction en cours…"
                        isDownloading -> "Téléchargement modèle vocal… ${voiceDownloadProgress}%"
                        !modelReady -> "Requis pour la reconnaissance hors-ligne"
                        voiceEnabled -> "Actif — dites « Portal » pour parler"
                        else -> "Désactivé"
                    },
                    color = if (voiceDownloadProgress == -1) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    fontSize = 12.sp, fontFamily = Mincho,
                )
            }
            Spacer(Modifier.width(10.dp))
            when {
                isDownloading -> {}
                !modelReady -> Box(Modifier.clickable { onVoiceDownload() }) {
                    Text("Télécharger", color = Shu, fontSize = 15.sp, fontFamily = Mincho)
                }
                else -> Switch(
                    checked = voiceEnabled, onCheckedChange = onVoiceToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = AccentShu, checkedTrackColor = AccentShu.copy(alpha = 0.4f)),
                )
            }
        }
        if (isDownloading) {
            LinearProgressIndicator(
                progress = { (voiceDownloadProgress ?: 0) / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = AccentShu,
                trackColor = AccentShu.copy(alpha = 0.2f),
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))

    SettingSwitch(
        text = "Détection de présence",
        subtitle = if (presenceEnabled) "Écran allumé si vous êtes devant" else "Désactivé",
        checked = presenceEnabled,
        onCheckedChange = onPresenceToggle,
    )
}

@Composable
private fun DevicePanelContent(
    verifierDisabled: Boolean,
    updateAvailable: String?,
    updatePct: Int,
    onVerifier: () -> Unit,
    onUpdate: () -> Unit,
    onSystem: () -> Unit,
) {
    SettingRow(
        text = if (verifierDisabled) "Vérificateur désactivé ✓" else "Désactiver le vérificateur",
        subtitle = "Requis pour FukkaStore et l'installation d'apps",
        chevron = !verifierDisabled,
    ) { if (!verifierDisabled) onVerifier() }

    if (updateAvailable != null) {
        val busy = updatePct in 0..100
        SettingRow(
            text = when {
                updatePct in 0..99 -> "Téléchargement… $updatePct%"
                updatePct == 100   -> "Installation en cours…"
                else               -> "Mise à jour disponible : $updateAvailable"
            },
            subtitle = if (busy) "Ne pas quitter l'application"
                       else "Version actuelle : ${BuildConfig.VERSION_NAME} — Appuyer pour installer",
            chevron = !busy,
        ) { if (!busy) onUpdate() }
    } else {
        SettingRow("Version ${BuildConfig.VERSION_NAME}", subtitle = "À jour", chevron = false) {}
    }
    SettingRow("Surveillance firmware", subtitle = FirmwareWatcher.currentBuild(), chevron = false) {}
    SettingRow("Réglages système", subtitle = "Paramètres Android") { onSystem() }
}

// ── Licences full-screen ─────────────────────────────────────────────────────

private data class LicenceItem(val name: String, val detail: String, val licence: String)

private val MUSIC = listOf(
    LicenceItem("Koto traditionnel japonais", "Amy (prettysleepy) — Pixabay", "Pixabay Content License"),
    LicenceItem("Bande originale anime japonaise", "Youssef Canar (yulius2tudio) — Pixabay", "Pixabay Content License"),
    LicenceItem("Musique de fond Asie / Japon", "Tunetank — Pixabay", "Pixabay Content License"),
)

private val TECHNOLOGIES = listOf(
    LicenceItem("Jetpack Compose", "Google", "Apache 2.0"),
    LicenceItem("AndroidX Room", "Google", "Apache 2.0"),
    LicenceItem("AndroidX DataStore", "Google", "Apache 2.0"),
    LicenceItem("AndroidX Navigation", "Google", "Apache 2.0"),
    LicenceItem("OkHttp", "Square, Inc.", "Apache 2.0"),
    LicenceItem("Coil", "Coil Contributors", "Apache 2.0"),
    LicenceItem("kotlinx.serialization", "JetBrains", "Apache 2.0"),
    LicenceItem("Vosk / vosk-android", "Alpha Cephei", "Apache 2.0"),
    LicenceItem("ZXing Core", "ZXing Authors", "Apache 2.0"),
    LicenceItem("JmDNS", "JmDNS Contributors", "LGPL 2.1"),
    LicenceItem("Bouncy Castle", "Legion of the Bouncy Castle", "MIT"),
    LicenceItem("Google Play API", "Aurora OSS", "GPL 3.0"),
)

private val REPOS = listOf(
    LicenceItem("Aurora Store", "Aurora OSS — inspiration FukkaStore", "GPL 3.0"),
    LicenceItem("AirReceiver", "Félix C. — implémentation AirPlay", "GPL 3.0"),
)

private val AUTRES = listOf(
    LicenceItem("Meta Portal (Android 9)", "Meta Platforms — matériel cible", "Propriétaire"),
    LicenceItem("Material Design 3", "Google — système de design", "Apache 2.0"),
)

@Composable
private fun CreditsScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HankoSeal("礼", size = 40.dp, onClick = onBack)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Licences & Crédits", fontFamily = Mincho, fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    "Fukkatsu No P  v${BuildConfig.VERSION_NAME}  •  Cyprien Brisset",
                    fontFamily = Mincho, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 48.dp, vertical = 24.dp),
        ) {
            LicenceSection("MUSIQUE", MUSIC)
            Spacer(Modifier.height(28.dp))
            LicenceSection("TECHNOLOGIES", TECHNOLOGIES)
            Spacer(Modifier.height(28.dp))
            LicenceSection("REPOS & RÉFÉRENCES", REPOS)
            Spacer(Modifier.height(28.dp))
            LicenceSection("AUTRES", AUTRES)

            Spacer(Modifier.height(48.dp))
            Text(
                "復活",
                fontFamily = Mincho, fontSize = 48.sp, color = Shu.copy(alpha = 0.12f),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun LicenceSection(title: String, items: List<LicenceItem>) {
    Text(
        title,
        fontSize = 10.sp, letterSpacing = 2.sp, fontFamily = Mincho,
        color = Shu, modifier = Modifier.padding(bottom = 10.dp),
    )
    items.forEach { item ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.name, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    item.detail,
                    fontSize = 12.sp, fontFamily = Mincho,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
            Text(
                item.licence,
                fontSize = 11.sp, fontFamily = Mincho,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)))
    }
}

// ── Shared composables ────────────────────────────────────────────────────────

@Composable
private fun SettingSwitch(
    text: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text, color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 12.sp, fontFamily = Mincho)
        }
        Switch(
            checked = checked, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = AccentShu, checkedTrackColor = AccentShu.copy(alpha = 0.4f)),
        )
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
}

@Composable
private fun SettingRow(text: String, subtitle: String? = null, chevron: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp).clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text, color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp)
            if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 12.sp, fontFamily = Mincho)
        }
        Spacer(Modifier.width(10.dp))
        if (chevron) Text("›", color = Shu, fontSize = 22.sp)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
}
