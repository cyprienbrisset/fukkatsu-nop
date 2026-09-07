package com.cyprienbrisset.fukkatsunop.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

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
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            com.cyprienbrisset.fukkatsunop.ui.home.HomeShell(
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onAddTile = { nav.navigate(Routes.TILE_EDIT) },
            )
        }
        composable(Routes.SETTINGS) {
            com.cyprienbrisset.fukkatsunop.ui.settings.SettingsScreen(
                onBack = { nav.popBackStack() },
                onTiles = { nav.navigate(Routes.TILE_EDIT) },
                onAlarms = { nav.navigate(Routes.ALARMS) },
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
            )
        }
        composable(Routes.ALARM_EDIT) {
            com.cyprienbrisset.fukkatsunop.ui.alarms.AlarmEditScreen(onDone = { nav.popBackStack() })
        }
        composable(Routes.DARK_SCHEDULE) {
            com.cyprienbrisset.fukkatsunop.ui.settings.DarkModeScheduleScreen(onBack = { nav.popBackStack() })
        }
    }
}
