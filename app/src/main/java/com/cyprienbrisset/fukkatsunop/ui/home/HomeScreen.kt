package com.cyprienbrisset.fukkatsunop.ui.home

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyprienbrisset.fukkatsunop.data.tile.TileEntity
import com.cyprienbrisset.fukkatsunop.data.tile.TileType
import com.cyprienbrisset.fukkatsunop.integration.NotificationBadgeRepository
import com.cyprienbrisset.fukkatsunop.launch.LaunchIntentResolver
import com.cyprienbrisset.fukkatsunop.system.DarkModeManager
import com.cyprienbrisset.fukkatsunop.system.DndController
import com.cyprienbrisset.fukkatsunop.system.ScreenLock
import com.cyprienbrisset.fukkatsunop.ui.sumi.SealIconButton
import com.cyprienbrisset.fukkatsunop.ui.sumi.SectionLabel
import com.cyprienbrisset.fukkatsunop.ui.sumi.VerticalVermilionRule
import com.cyprienbrisset.fukkatsunop.ui.sumi.WatermarkKanji
import com.cyprienbrisset.fukkatsunop.ui.theme.Kinari
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho
import com.cyprienbrisset.fukkatsunop.ui.theme.OnShu
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu
import com.cyprienbrisset.fukkatsunop.airplay.AirPlayService
import com.cyprienbrisset.fukkatsunop.airplay.AirPlayState
import com.cyprienbrisset.fukkatsunop.ui.airplay.AirPlayActivity
import com.cyprienbrisset.fukkatsunop.ui.screensaver.SumiSaverActivity
import com.cyprienbrisset.fukkatsunop.web.WebAppActivity

@Composable
fun HomeScreen(onOpenSettings: () -> Unit, onAddTile: () -> Unit, onOpenAlarms: () -> Unit = {}, vm: HomeViewModel = viewModel()) {
    val ctx = LocalContext.current
    val weatherVm: WeatherViewModel = viewModel()
    val nowPlayingVm: NowPlayingViewModel = viewModel()
    val tiles by vm.tiles.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val weather by weatherVm.weather.collectAsStateWithLifecycle()
    val weatherError by weatherVm.weatherError.collectAsStateWithLifecycle()
    val weatherFetchedAt by weatherVm.weatherFetchedAt.collectAsStateWithLifecycle()
    val nextAlarm by vm.nextAlarm.collectAsStateWithLifecycle()
    val currentCity by weatherVm.currentCity.collectAsStateWithLifecycle()
    val weatherCities by weatherVm.weatherCities.collectAsStateWithLifecycle()
    val weatherIndex by weatherVm.weatherIndex.collectAsStateWithLifecycle()
    val nowPlaying by nowPlayingVm.nowPlaying.collectAsStateWithLifecycle()
    val recentContacts by vm.recentContacts.collectAsStateWithLifecycle()
    val badgeCounts by NotificationBadgeRepository.counts.collectAsStateWithLifecycle()
    var showDndDuration by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showRecents by remember { mutableStateOf(false) }
    val recentApps by vm.recentApps.collectAsStateWithLifecycle()
    val airPlayState by vm.airPlayState.collectAsStateWithLifecycle()
    var quickActionsTile by remember { mutableStateOf<TileEntity?>(null) }
    var reorderMode by remember { mutableStateOf(false) }
    val isDark by DarkModeManager.isDarkFlow.collectAsState()
    val saverMode by vm.saverMode.collectAsStateWithLifecycle()

    LaunchedEffect(airPlayState) {
        // Mirror mode only — extended display is auto-launched by AirPlayService
        val st = airPlayState
        if (st is AirPlayState.Streaming && !st.isExtended) {
            ctx.startActivity(Intent(ctx, AirPlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(AirPlayActivity.EXTRA_EXTENDED, false)
            })
        }
    }

    val launch: (TileEntity) -> Unit = { tile ->
        when (tile.type) {
            TileType.APP -> {
                val pkg = tile.packageName
                if (pkg == null || !LaunchIntentResolver.launch(ctx, pkg))
                    Toast.makeText(ctx, "App introuvable : ${tile.label}", Toast.LENGTH_SHORT).show()
                else
                    vm.recordLaunch(pkg, tile.label)
            }
            TileType.WEB -> ctx.startActivity(
                Intent(ctx, WebAppActivity::class.java)
                    .putExtra(WebAppActivity.EXTRA_URL, tile.url)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            TileType.AIRPLAY -> {
                val st = vm.airPlayState.value
                if (st is AirPlayState.Streaming) {
                    ctx.startActivity(Intent(ctx, AirPlayActivity::class.java)
                        .putExtra(AirPlayActivity.EXTRA_EXTENDED, st.isExtended))
                }
                if (st is AirPlayState.Error) {
                    ctx.startService(Intent(ctx, AirPlayService::class.java))
                }
                // Waiting/Connecting: no-op, service is already running
            }
        }
    }

    val swipeModifier = Modifier.pointerInput(Unit) {
        var totalY = 0f
        detectDragGestures(
            onDragEnd = {
                when {
                    totalY < -80f -> showSearch = true
                    totalY > 80f -> {
                        if (DndController.isGranted(ctx)) {
                            if (DndController.isDndOn(ctx)) DndController.disable(ctx)
                            else showDndDuration = true
                        }
                    }
                }
                totalY = 0f
            },
            onDragCancel = { totalY = 0f },
        ) { _, delta -> totalY += delta.y }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var accumulated = 1f
                detectTransformGestures { _, _, zoom, _ ->
                    accumulated *= zoom
                    if (accumulated > 1.35f) {
                        showRecents = true
                        accumulated = 1f
                    }
                    if (accumulated < 0.8f) accumulated = 1f
                }
            },
    ) {
        val landscape = maxWidth > maxHeight
        val isCompact = maxWidth < 1500.dp   // Portal Go/Mini (1280dp) vs Portal+ 1st gen (1920dp)
        // NowPlaying est rafraîchi dans le ViewModel (toutes les 5s), pas sur chaque tick d'horloge.
        WatermarkKanji("墨", Modifier.align(Alignment.BottomEnd).offset(x = (-64).dp, y = (-10).dp))
        if (landscape) {
            Row(Modifier.fillMaxSize().padding(
                start = if (isCompact) 28.dp else 46.dp,
                top = if (isCompact) 28.dp else 44.dp,
                bottom = if (isCompact) 28.dp else 40.dp,
                end = if (isCompact) 28.dp else 40.dp,
            )) {
                Column(Modifier.fillMaxHeight().weight(0.38f)) {
                    HomeBranding(
                        portrait = false,
                        compact = isCompact,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 8.dp)
                            .then(swipeModifier),
                    )
                    Spacer(Modifier.height(if (isCompact) 12.dp else 20.dp))
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AmbientBanner(
                            now, weather,
                            nextAlarm = nextAlarm,
                            portrait = false,
                            compact = isCompact,
                            onClockClick = onOpenAlarms,
                            cityName = currentCity?.city,
                            cityIndex = weatherIndex,
                            citiesCount = weatherCities.size,
                            onNextCity = { weatherVm.nextWeatherCity() },
                            onPrevCity = { weatherVm.prevWeatherCity() },
                            onRefreshWeather = { weatherVm.refreshWeather() },
                            weatherFetchedAt = weatherFetchedAt,
                            weatherError = weatherError,
                        )
                        val np = nowPlaying
                        if (np != null) {
                            Spacer(Modifier.height(20.dp))
                            NowPlayingBar(
                                np,
                                onPrev = { nowPlayingVm.prev() },
                                onToggle = { nowPlayingVm.toggle() },
                                onNext = { nowPlayingVm.next() },
                                onSeek = { nowPlayingVm.seekTo(it) },
                                onOpenApp = { np.packageName?.let { p -> LaunchIntentResolver.launch(ctx, p) } },
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (recentContacts.isNotEmpty()) {
                        RecentContactsStrip(recentContacts)
                        Spacer(Modifier.height(16.dp))
                    }
                    val dndOnL = remember(now) { DndController.isGranted(ctx) && DndController.isDndOn(ctx) }
                    Row(
                        Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SealIconButton(
                            icon = if (dndOnL) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications,
                            contentDescription = "Ne pas déranger",
                            active = dndOnL,
                            onClick = {
                                if (!DndController.isGranted(ctx)) DndController.toggleOrRequest(ctx)
                                else if (dndOnL) DndController.disable(ctx)
                                else showDndDuration = true
                            },
                        )
                        SealIconButton(
                            icon = Icons.Rounded.PowerSettingsNew,
                            contentDescription = "Éteindre l'écran",
                            onClick = {
                                if (saverMode) ctx.startActivity(Intent(ctx, SumiSaverActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                else ScreenLock.lockOrRequest(ctx)
                            },
                        )
                        SealIconButton(
                            icon = Icons.Rounded.Settings,
                            contentDescription = "Réglages",
                            onClick = onOpenSettings,
                        )
                        SealIconButton(
                            icon = if (isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                            contentDescription = if (isDark) "Mode jour" else "Mode nuit",
                            onClick = { DarkModeManager.apply(ctx, !isDark) },
                        )
                    }
                }
                VerticalVermilionRule(Modifier.align(Alignment.CenterVertically).padding(horizontal = 8.dp), length = if (isCompact) 160.dp else 220.dp)
                Column(Modifier.fillMaxHeight().weight(0.62f).padding(start = if (isCompact) 18.dp else 30.dp), verticalArrangement = Arrangement.Center) {
                    SectionLabel("アプリ", "MES APPS")
                    Spacer(Modifier.height(if (isCompact) 14.dp else 22.dp))
                    MedallionGrid(
                        tiles = tiles,
                        minCellWidth = if (isCompact) 90.dp else 108.dp,
                        onTileClick = launch,
                        onLongClick = { tile -> quickActionsTile = tile },
                        onAddClick = onAddTile,
                        badgeCounts = badgeCounts,
                        reorderMode = reorderMode,
                        onReorder = { vm.reorderTiles(it) },
                        onEnterReorder = { reorderMode = true },
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = if (isCompact) 20.dp else 32.dp, vertical = if (isCompact) 24.dp else 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(if (isCompact) 10.dp else 20.dp))
                HomeBranding(portrait = true, compact = isCompact, modifier = swipeModifier)
                Spacer(Modifier.height(if (isCompact) 14.dp else 24.dp))
                AmbientBanner(
                    now, weather,
                    nextAlarm = nextAlarm,
                    portrait = true,
                    compact = isCompact,
                    onClockClick = onOpenAlarms,
                    cityName = currentCity?.city,
                    cityIndex = weatherIndex,
                    citiesCount = weatherCities.size,
                    onNextCity = { weatherVm.nextWeatherCity() },
                    onPrevCity = { weatherVm.prevWeatherCity() },
                    onRefreshWeather = { weatherVm.refreshWeather() },
                    weatherFetchedAt = weatherFetchedAt,
                    weatherError = weatherError,
                )
                val np = nowPlaying
                if (np != null) {
                    Spacer(Modifier.height(18.dp))
                    NowPlayingBar(
                    np,
                    onPrev = { nowPlayingVm.prev() },
                    onToggle = { nowPlayingVm.toggle() },
                    onNext = { nowPlayingVm.next() },
                    onSeek = { nowPlayingVm.seekTo(it) },
                    onOpenApp = { np.packageName?.let { p -> LaunchIntentResolver.launch(ctx, p) } },
                )
                }
                if (recentContacts.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    RecentContactsStrip(recentContacts)
                }
                Spacer(Modifier.height(if (isCompact) 16.dp else 28.dp))
                SectionLabel("アプリ", "MES APPS")
                Spacer(Modifier.height(if (isCompact) 10.dp else 18.dp))
                MedallionGrid(
                    tiles = tiles,
                    minCellWidth = if (isCompact) 88.dp else 104.dp,
                    onTileClick = launch,
                    onLongClick = { tile -> quickActionsTile = tile },
                    onAddClick = onAddTile,
                    modifier = Modifier.weight(1f),
                    badgeCounts = badgeCounts,
                    reorderMode = reorderMode,
                    onReorder = { vm.reorderTiles(it) },
                    onEnterReorder = { reorderMode = true },
                )
            }
        }

        val weatherEffects by vm.weatherEffectsEnabled.collectAsStateWithLifecycle()
        WeatherOverlay(
            weather = if (weatherEffects) weather else null,
            isDark = isDark,
            modifier = Modifier.fillMaxSize(),
        )

        // Portrait overlay buttons
        if (!landscape) {
            val dndOn = remember(now) { DndController.isGranted(ctx) && DndController.isDndOn(ctx) }
            Row(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 40.dp, end = 34.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SealIconButton(
                    icon = if (dndOn) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications,
                    contentDescription = "Ne pas déranger",
                    active = dndOn,
                    onClick = {
                        if (!DndController.isGranted(ctx)) DndController.toggleOrRequest(ctx)
                        else if (dndOn) DndController.disable(ctx)
                        else showDndDuration = true
                    },
                )
                SealIconButton(
                    icon = Icons.Rounded.PowerSettingsNew,
                    contentDescription = "Éteindre l'écran",
                    onClick = { ScreenLock.lockOrRequest(ctx) },
                )
                SealIconButton(
                    icon = Icons.Rounded.Settings,
                    contentDescription = "Réglages",
                    onClick = onOpenSettings,
                )
                SealIconButton(
                    icon = if (isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                    contentDescription = if (isDark) "Mode jour" else "Mode nuit",
                    onClick = { DarkModeManager.apply(ctx, !isDark) },
                )
            }
        }

        if (showDndDuration) {
            DndDurationDialog(
                onDismiss = { showDndDuration = false },
                onPick = { millis -> DndController.enableFor(ctx, millis); showDndDuration = false },
            )
        }

        // Quick actions sheet (long press on tile)
        val qaTile = quickActionsTile
        if (qaTile != null) {
            val tileIndex = tiles.indexOfFirst { it.id == qaTile.id }
            QuickActionsSheet(
                tile = qaTile,
                tileIndex = tileIndex,
                totalTiles = tiles.size,
                onDismiss = { quickActionsTile = null },
                onRemove = {
                    vm.deleteTile(qaTile)
                    quickActionsTile = null
                },
                onMoveUp = {
                    if (tileIndex > 0) {
                        val reordered = tiles.toMutableList().also { list ->
                            val item = list.removeAt(tileIndex)
                            list.add(tileIndex - 1, item)
                        }
                        vm.reorderTiles(reordered)
                    }
                    quickActionsTile = null
                },
                onMoveDown = {
                    if (tileIndex < tiles.size - 1) {
                        val reordered = tiles.toMutableList().also { list ->
                            val item = list.removeAt(tileIndex)
                            list.add(tileIndex + 1, item)
                        }
                        vm.reorderTiles(reordered)
                    }
                    quickActionsTile = null
                },
                onStartReorder = {
                    reorderMode = true
                    quickActionsTile = null
                },
            )
        }

        // Reorder mode: floating "Terminé" button
        if (reorderMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Shu)
                    .clickable { reorderMode = false }
                    .padding(horizontal = 36.dp, vertical = 14.dp),
            ) {
                Text("Terminé", color = OnShu, fontFamily = Mincho, fontSize = 16.sp)
            }
        }

        // App search overlay (swipe up on HomeBranding)
        if (showSearch) {
            AppSearchOverlay(onDismiss = { showSearch = false })
        }

        // Multitask overlay (pinch-out gesture)
        if (showRecents) {
            RecentAppsOverlay(
                apps = recentApps,
                onDismiss = { showRecents = false },
                onLaunchApp = { pkg -> LaunchIntentResolver.launch(ctx, pkg) },
                onDismissApp = { pkg -> vm.removeRecent(pkg) },
                onClearAll = { vm.clearRecents() },
            )
        }

        // Right-edge brightness swipe control (within the outer padding area — no tile overlap)
        BrightnessEdgeControl(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(if (landscape) 40.dp else 32.dp),
        )
    }
}
