package com.cyprienbrisset.fukkatsunop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cyprienbrisset.fukkatsunop.MainActivity
import com.cyprienbrisset.fukkatsunop.system.UpdateProgress
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiBackdrop
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiColors
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiScreen
import com.cyprienbrisset.fukkatsunop.ui.fuji.FitToPortal
import com.cyprienbrisset.fukkatsunop.ui.theme.Gothic

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val TILE_EDIT = "tile_edit"
    const val ALARMS = "alarms"
    const val ALARM_EDIT = "alarm_edit"
    const val STORE = "store"
    const val INSTALLED_APPS = "installed_apps"
    const val DARK_SCHEDULE = "dark_schedule"
    const val AGENDA = "agenda"
    const val WIDGETS = "widgets"
    const val SEARCH = "search"
    const val MAC = "mac"
}

/**
 * Screens written before the Fuji redesign were sized for close-up use; render them
 * 30 % larger so their text and touch targets stay comfortable from a distance.
 */
@Composable
fun LegacyScale(content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density * 1.3f, d.fontScale), content = content)
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val updatePct by UpdateProgress.pct.collectAsState()
    val entry by nav.currentBackStackEntryAsState()
    val onHome = entry?.destination?.route.let { it == null || it == Routes.HOME }

    // Back to the ambient screen when the Home key is pressed (onNewIntent in MainActivity).
    val goHome by MainActivity.goHome.collectAsState()
    LaunchedEffect(goHome) {
        if (goHome > 0) nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = false } }
    }
    val updating = updatePct in 0..100
    val back: () -> Unit = { nav.popBackStack() }

    FitToPortal {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Every screen but the ambient home sits on the blurred photo of the moment.
        if (!onHome) FujiBackdrop()

        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            enterTransition = { scaleIn(spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow), initialScale = 0.96f) + fadeIn(tween(260)) },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { scaleOut(tween(220), targetScale = 0.96f) + fadeOut(tween(220)) },
        ) {
            composable(Routes.HOME) {
                com.cyprienbrisset.fukkatsunop.ui.home.HomeShell(
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onOpenUpdates = { nav.navigate(Routes.SETTINGS + "/update") },
                    onAddTile = { nav.navigate(Routes.TILE_EDIT) },
                    onOpenAlarms = { nav.navigate(Routes.ALARMS) },
                    onOpenAgenda = { nav.navigate(Routes.AGENDA) },
                    onOpenWidgets = { nav.navigate(Routes.WIDGETS) },
                    onOpenSearch = { nav.navigate(Routes.SEARCH) },
                    onOpenMac = { nav.navigate(Routes.MAC) },
                )
            }
            composable(Routes.SETTINGS) {
                com.cyprienbrisset.fukkatsunop.ui.settings.SettingsScreen(
                    onBack = back,
                    onTiles = { nav.navigate(Routes.TILE_EDIT) },
                    onWeather = { nav.navigate(Routes.SETTINGS + "/weather") },
                    onStore = { nav.navigate(Routes.STORE) },
                    onInstalledApps = { nav.navigate(Routes.INSTALLED_APPS) },
                    onDarkSchedule = { nav.navigate(Routes.DARK_SCHEDULE) },
                )
            }
            composable(Routes.AGENDA) {
                com.cyprienbrisset.fukkatsunop.ui.google.GoogleScreen(onBack = back)
            }
            composable(Routes.WIDGETS) {
                FujiScreen("Widgets", onBack = back) {
                    LegacyScale { com.cyprienbrisset.fukkatsunop.ui.widgets.WidgetDashboard() }
                }
            }
            composable(Routes.SEARCH) {
                com.cyprienbrisset.fukkatsunop.ui.home.AllAppsScreen(onBack = back)
            }
            composable(Routes.MAC) {
                com.cyprienbrisset.fukkatsunop.ui.airplay.MacWaitingScreen(onClose = back)
            }
            composable(Routes.STORE) {
                FujiScreen("FukkaStore", onBack = back) {
                    LegacyScale { com.cyprienbrisset.fukkatsunop.ui.store.StoreBody() }
                }
            }
            composable(Routes.INSTALLED_APPS) {
                LegacyScale { com.cyprienbrisset.fukkatsunop.ui.settings.InstalledAppsScreen(onBack = back) }
            }
            composable(Routes.TILE_EDIT) {
                LegacyScale { com.cyprienbrisset.fukkatsunop.ui.settings.TileEditScreen(onBack = back) }
            }
            composable(Routes.SETTINGS + "/update") {
                com.cyprienbrisset.fukkatsunop.ui.settings.SettingsScreen(
                    onBack = back,
                    onTiles = { nav.navigate(Routes.TILE_EDIT) },
                    onWeather = { nav.navigate(Routes.SETTINGS + "/weather") },
                    onStore = { nav.navigate(Routes.STORE) },
                    onInstalledApps = { nav.navigate(Routes.INSTALLED_APPS) },
                    onDarkSchedule = { nav.navigate(Routes.DARK_SCHEDULE) },
                    openUpdates = true,
                )
            }
            composable(Routes.AGENDA) {
                com.cyprienbrisset.fukkatsunop.ui.google.GoogleScreen(onBack = back)
            }
            composable(Routes.WIDGETS) {
                FujiScreen("Widgets", onBack = back) {
                    LegacyScale { com.cyprienbrisset.fukkatsunop.ui.widgets.WidgetDashboard() }
                }
            }
            composable(Routes.SEARCH) {
                com.cyprienbrisset.fukkatsunop.ui.home.AllAppsScreen(onBack = back)
            }
            composable(Routes.MAC) {
                com.cyprienbrisset.fukkatsunop.ui.airplay.MacWaitingScreen(onClose = back)
            }
            composable(Routes.STORE) {
                FujiScreen("FukkaStore", onBack = back) {
                    LegacyScale { com.cyprienbrisset.fukkatsunop.ui.store.StoreBody() }
                }
            }
            composable(Routes.INSTALLED_APPS) {
                LegacyScale { com.cyprienbrisset.fukkatsunop.ui.settings.InstalledAppsScreen(onBack = back) }
            }
            composable(Routes.TILE_EDIT) {
                LegacyScale { com.cyprienbrisset.fukkatsunop.ui.settings.TileEditScreen(onBack = back) }
            }
            composable(Routes.SETTINGS + "/weather") {
                LegacyScale { com.cyprienbrisset.fukkatsunop.ui.settings.WeatherSettingsScreen(onBack = back) }
            }
            composable(Routes.ALARMS) {
                com.cyprienbrisset.fukkatsunop.ui.alarms.AlarmsScreen(
                    onBack = back,
                    onAdd = { nav.navigate(Routes.ALARM_EDIT) },
                    onEdit = { id -> nav.navigate("${Routes.ALARM_EDIT}?id=$id") },
                )
            }
            composable(
                route = "${Routes.ALARM_EDIT}?id={id}",
                arguments = listOf(navArgument("id") {
                    type = NavType.LongType
                    defaultValue = 0L
                }),
            ) { backStackEntry ->
                val alarmId = backStackEntry.arguments?.getLong("id") ?: 0L
                com.cyprienbrisset.fukkatsunop.ui.alarms.AlarmEditScreen(alarmId = alarmId, onDone = back)
            }
            composable(Routes.DARK_SCHEDULE) {
                LegacyScale { com.cyprienbrisset.fukkatsunop.ui.settings.DarkModeScheduleScreen(onBack = back) }
            }
        }

        AnimatedVisibility(visible = updating, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            UpdateOverlay(pct = updatePct)
        }
    }
    }
}

@Composable
private fun UpdateOverlay(pct: Int) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE60A0E18))
            .pointerInput(Unit) { detectTapGestures(onLongPress = { UpdateProgress.set(-1) }) },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.padding(48.dp),
        ) {
            Text(
                text = if (pct < 100) "Mise à jour en cours" else "Installation…",
                color = FujiColors.Cream, fontFamily = Gothic, fontSize = 44.sp,
            )
            val bar = Modifier.fillMaxWidth(0.5f).height(16.dp).clip(RoundedCornerShape(8.dp))
            if (pct < 100) {
                LinearProgressIndicator(progress = { pct / 100f }, modifier = bar, color = FujiColors.Cream, trackColor = FujiColors.GlassStrong)
                Text("$pct %", color = FujiColors.Cream, fontFamily = Gothic, fontSize = 64.sp)
            } else {
                LinearProgressIndicator(modifier = bar, color = FujiColors.Cream, trackColor = FujiColors.GlassStrong)
            }
            Text(
                "Ne pas éteindre le Portal ni quitter l'application",
                color = FujiColors.Muted, fontFamily = Gothic, fontSize = 24.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Text("Appui long pour annuler", color = FujiColors.Faint, fontFamily = Gothic, fontSize = 20.sp)
        }
    }
}
