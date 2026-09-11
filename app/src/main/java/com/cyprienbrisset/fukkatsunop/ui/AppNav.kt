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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cyprienbrisset.fukkatsunop.MainActivity
import com.cyprienbrisset.fukkatsunop.system.UpdateProgress
import com.cyprienbrisset.fukkatsunop.ui.theme.AccentShu
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiMuted

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val TILE_EDIT = "tile_edit"
    const val ALARMS = "alarms"
    const val ALARM_EDIT = "alarm_edit"
    const val STORE = "store"
    const val INSTALLED_APPS = "installed_apps"
    const val DARK_SCHEDULE = "dark_schedule"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val updatePct by UpdateProgress.pct.collectAsState()

    // Retour à l'accueil quand l'utilisateur appuie sur Home (onNewIntent dans MainActivity).
    val goHome by MainActivity.goHome.collectAsState()
    LaunchedEffect(goHome) {
        if (goHome > 0) nav.navigate(Routes.HOME) {
            popUpTo(Routes.HOME) { inclusive = false }
        }
    }
    val updating = updatePct in 0..100

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            enterTransition = { scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), initialScale = 0.94f) + fadeIn(tween(260)) },
            exitTransition = { scaleOut(tween(200), targetScale = 0.97f) + fadeOut(tween(200)) },
            popEnterTransition = { scaleIn(tween(220), initialScale = 0.97f) + fadeIn(tween(220)) },
            popExitTransition = { scaleOut(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), targetScale = 0.94f) + fadeOut(tween(260)) },
        ) {
            composable(Routes.HOME) {
                com.cyprienbrisset.fukkatsunop.ui.home.HomeShell(
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onAddTile = { nav.navigate(Routes.TILE_EDIT) },
                    onOpenAlarms = { nav.navigate(Routes.ALARMS) },
                )
            }
            composable(Routes.SETTINGS) {
                com.cyprienbrisset.fukkatsunop.ui.settings.SettingsScreen(
                    onBack = { nav.popBackStack() },
                    onTiles = { nav.navigate(Routes.TILE_EDIT) },
                    onWeather = { nav.navigate(Routes.SETTINGS + "/weather") },
                    onStore = { nav.navigate(Routes.STORE) },
                    onInstalledApps = { nav.navigate(Routes.INSTALLED_APPS) },
                    onDarkSchedule = { nav.navigate(Routes.DARK_SCHEDULE) },
                )
            }
            composable(Routes.INSTALLED_APPS) {
                com.cyprienbrisset.fukkatsunop.ui.settings.InstalledAppsScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.STORE) {
                com.cyprienbrisset.fukkatsunop.ui.store.StoreScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.TILE_EDIT) {
                com.cyprienbrisset.fukkatsunop.ui.settings.TileEditScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.SETTINGS + "/weather") {
                com.cyprienbrisset.fukkatsunop.ui.settings.WeatherSettingsScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.ALARMS) {
                com.cyprienbrisset.fukkatsunop.ui.alarms.AlarmsScreen(
                    onBack = { nav.popBackStack() },
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
                com.cyprienbrisset.fukkatsunop.ui.alarms.AlarmEditScreen(
                    alarmId = alarmId,
                    onDone = { nav.popBackStack() },
                )
            }
            composable(Routes.DARK_SCHEDULE) {
                com.cyprienbrisset.fukkatsunop.ui.settings.DarkModeScheduleScreen(onBack = { nav.popBackStack() })
            }
        }

        AnimatedVisibility(
            visible = updating,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            UpdateOverlay(pct = updatePct)
        }
    }
}

@Composable
private fun UpdateOverlay(pct: Int) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { UpdateProgress.set(-1) })
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(48.dp),
        ) {
            Text(
                text = if (pct < 100) "Mise à jour en cours" else "Installation…",
                color = Color.White,
                fontFamily = Mincho,
                fontSize = 22.sp,
            )

            if (pct < 100) {
                LinearProgressIndicator(
                    progress = { pct / 100f },
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = AccentShu,
                    trackColor = AccentShu.copy(alpha = 0.25f),
                )
                Text(
                    text = "$pct%",
                    color = AccentShu,
                    fontFamily = Mincho,
                    fontSize = 32.sp,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = AccentShu,
                    trackColor = AccentShu.copy(alpha = 0.25f),
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = "Ne pas éteindre ou quitter l'application",
                color = SumiMuted,
                fontFamily = Mincho,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Appui long pour annuler",
                color = SumiMuted.copy(alpha = 0.4f),
                fontFamily = Mincho,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
