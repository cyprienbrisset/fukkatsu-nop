package com.cyprienbrisset.fukkatsunop.ui.home

import android.app.Activity
import android.content.Intent
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyprienbrisset.fukkatsunop.airplay.AirPlayService
import com.cyprienbrisset.fukkatsunop.airplay.AirPlayState
import com.cyprienbrisset.fukkatsunop.data.tile.TileEntity
import com.cyprienbrisset.fukkatsunop.data.tile.TileType
import com.cyprienbrisset.fukkatsunop.integration.NotificationBadgeRepository
import com.cyprienbrisset.fukkatsunop.integration.google.CalendarEvent
import com.cyprienbrisset.fukkatsunop.launch.LaunchIntentResolver
import com.cyprienbrisset.fukkatsunop.media.NowPlaying
import com.cyprienbrisset.fukkatsunop.system.DndController
import com.cyprienbrisset.fukkatsunop.system.ScreenLock
import com.cyprienbrisset.fukkatsunop.system.UpdateChecker
import com.cyprienbrisset.fukkatsunop.system.voice.VoiceService
import com.cyprienbrisset.fukkatsunop.ui.airplay.AirPlayActivity
import com.cyprienbrisset.fukkatsunop.ui.LegacyScale
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiColors
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiPhoto
import com.cyprienbrisset.fukkatsunop.ui.fuji.rememberMoment
import com.cyprienbrisset.fukkatsunop.ui.google.GoogleViewModel
import com.cyprienbrisset.fukkatsunop.ui.google.TabState
import com.cyprienbrisset.fukkatsunop.ui.screensaver.SumiSaverActivity
import com.cyprienbrisset.fukkatsunop.ui.theme.Gothic
import com.cyprienbrisset.fukkatsunop.web.WebAppActivity
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Panel { NONE, APPS, CONTROLS }

private val FR = Locale.FRENCH
private val TimeFmt = DateTimeFormatter.ofPattern("HH:mm")
private val DateFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", FR)

private val Lifted = TextStyle(shadow = Shadow(Color(0x73000000), blurRadius = 24f))

/**
 * Ambient home: the Fuji photo of the moment, the time, and a rotating card.
 * Touching the screen opens the apps; pulling from the top opens the controls.
 */
@Composable
fun HomeShell(
    onOpenSettings: () -> Unit,
    onAddTile: () -> Unit,
    onOpenAlarms: () -> Unit = {},
    onOpenAgenda: () -> Unit = {},
    onOpenWidgets: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenMac: () -> Unit = {},
    vm: HomeViewModel = viewModel(),
) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val weatherVm: WeatherViewModel = viewModel()
    val nowPlayingVm: NowPlayingViewModel = viewModel()
    val googleVm: GoogleViewModel = viewModel(factory = GoogleViewModel.Factory(ctx))

    val now by vm.now.collectAsStateWithLifecycle()
    val tiles by vm.tiles.collectAsStateWithLifecycle()
    val nextAlarm by vm.nextAlarm.collectAsStateWithLifecycle()
    val recentContacts by vm.recentContacts.collectAsStateWithLifecycle()
    val recentApps by vm.recentApps.collectAsStateWithLifecycle()
    val ramFreed by vm.ramFreedMb.collectAsStateWithLifecycle()
    val airPlayState by vm.airPlayState.collectAsStateWithLifecycle()
    val saverMode by vm.saverMode.collectAsStateWithLifecycle()
    val weatherEffects by vm.weatherEffectsEnabled.collectAsStateWithLifecycle()
    val weather by weatherVm.weather.collectAsStateWithLifecycle()
    val city by weatherVm.currentCity.collectAsStateWithLifecycle()
    val cities by weatherVm.weatherCities.collectAsStateWithLifecycle()
    val nowPlaying by nowPlayingVm.nowPlaying.collectAsStateWithLifecycle()
    val google by googleVm.state.collectAsStateWithLifecycle()
    val badgeCounts by NotificationBadgeRepository.counts.collectAsStateWithLifecycle()
    val voiceState by VoiceService.state.collectAsState()
    val moment = rememberMoment()

    var panel by remember { mutableStateOf(Panel.NONE) }
    var hudKind by remember { mutableStateOf<LevelKind?>(null) }
    var hudValue by remember { mutableStateOf(0f) }
    var showDndDuration by remember { mutableStateOf(false) }
    var quickActionsTile by remember { mutableStateOf<TileEntity?>(null) }
    var updateVersion by remember { mutableStateOf<String?>(null) }
    val dndOn = remember(now.minute, panel) { DndController.isGranted(ctx) && DndController.isDndOn(ctx) }

    // Refresh the "open apps" row each time the panel opens; forget the freed-memory note when it closes.
    LaunchedEffect(panel) {
        if (panel == Panel.APPS) vm.refreshOpenApps() else vm.clearRamFreed()
    }

    LaunchedEffect(Unit) {
        UpdateChecker.check(ctx)
        updateVersion = UpdateChecker.availableVersionName(ctx)
    }

    // Back closes the apps panel or the control centre.
    androidx.activity.compose.BackHandler(enabled = panel != Panel.NONE) { panel = Panel.NONE }

    // The video screen itself is opened by AirPlayService when the stream starts.
    LaunchedEffect(airPlayState) {
        val st = airPlayState
        // As soon as a Mac starts connecting, show the "ready to be a display" screen.
        if (st is AirPlayState.Connecting) onOpenMac()
    }

    // Idle: close panels after 1 min, dim the screen after 3 min.
    var lastTouch by remember { mutableStateOf(System.currentTimeMillis()) }
    var dimmed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            val idle = System.currentTimeMillis() - lastTouch
            if (idle > 60_000 && panel != Panel.NONE) panel = Panel.NONE
            if (!dimmed && idle > 3 * 60_000) dimmed = true
        }
    }
    LaunchedEffect(dimmed) {
        val window = activity?.window ?: return@LaunchedEffect
        val from = window.attributes.screenBrightness.let { if (it <= 0) 1f else it }
        if (dimmed) {
            fadeBrightness(window, from, 0.25f, 3000L, 60)
        } else {
            if (from < 0.98f) fadeBrightness(window, from, 1f, 700L, 28)
            window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }

    val launch: (TileEntity) -> Unit = { tile ->
        when (tile.type) {
            TileType.APP -> {
                val pkg = tile.packageName
                if (pkg == null || !LaunchIntentResolver.launch(ctx, pkg))
                    Toast.makeText(ctx, "App introuvable : ${tile.label}", Toast.LENGTH_SHORT).show()
                else vm.recordLaunch(pkg, tile.label)
            }
            TileType.WEB -> ctx.startActivity(
                Intent(ctx, WebAppActivity::class.java)
                    .putExtra(WebAppActivity.EXTRA_URL, tile.url)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            TileType.AIRPLAY -> {
                when (val st = vm.airPlayState.value) {
                    is AirPlayState.Streaming -> ctx.startActivity(
                        Intent(ctx, AirPlayActivity::class.java).putExtra(AirPlayActivity.EXTRA_EXTENDED, st.isExtended),
                    )
                    is AirPlayState.Error -> ctx.startService(Intent(ctx, AirPlayService::class.java))
                    // Waiting / connecting: show the "how to connect" screen.
                    else -> onOpenMac()
                }
            }
        }
    }

    val nextEvent: CalendarEvent? = (google.agenda as? TabState.Success)?.data
        ?.firstOrNull { !it.isAllDay && it.end.isAfter(Instant.now()) && Duration.between(Instant.now(), it.start).toHours() < 24 }

    val timeText = now.format(TimeFmt)
    val dateText = now.format(DateFmt).replaceFirstChar { it.titlecase(FR) }
    val weatherText = weather?.let { w -> listOfNotNull("${w.temperatureC}°", w.description, city?.city).joinToString(" · ") }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitPointerEvent(PointerEventPass.Initial)
                    lastTouch = System.currentTimeMillis()
                    if (dimmed) dimmed = false
                }
            },
    ) {
        FujiPhoto(moment, Modifier.fillMaxSize())
        // Soft scrim so the white clock reads on bright skies.
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    0f to Color(0x8C080A12), 0.38f to Color(0x26080A12), 0.55f to Color(0x00080A12), 1f to Color(0x59080A12),
                ),
            ),
        )
        WeatherOverlay(weather = if (weatherEffects) weather else null, isDark = true, modifier = Modifier.fillMaxSize())

        // ── Ambient layer: tap → apps, edge swipes → controls / apps, side slides → brightness / volume
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { panel = Panel.APPS } }
                .pointerInput(Unit) {
                    // From the top edge → controls, from the bottom edge → apps.
                    // Elsewhere, like Netflix: left half = brightness, right half = volume.
                    var dy = 0f
                    var start = 0f
                    var mode: LevelKind? = null
                    var edge = 0
                    detectVerticalDragGestures(
                        onDragStart = { o ->
                            dy = 0f
                            val band = 160.dp.toPx()
                            edge = when { o.y < band -> 1; o.y > size.height - band -> 2; else -> 0 }
                            mode = if (edge != 0) null else if (o.x < size.width / 2) LevelKind.BRIGHTNESS else LevelKind.VOLUME
                            start = when (mode) {
                                LevelKind.BRIGHTNESS -> DeviceLevels.brightness(ctx)
                                LevelKind.VOLUME -> DeviceLevels.volume(ctx)
                                null -> 0f
                            }
                            hudKind = mode
                            hudValue = start
                        },
                        onDragEnd = {
                            when {
                                edge == 1 && dy > 120f -> panel = Panel.CONTROLS
                                edge == 2 && dy < -120f -> panel = Panel.APPS
                            }
                            hudKind = null
                        },
                        onDragCancel = { hudKind = null },
                    ) { change, d ->
                        dy += d
                        val kind = mode ?: return@detectVerticalDragGestures
                        change.consume()
                        val v = (start - dy / (size.height * 0.6f)).coerceIn(0f, 1f)
                        hudValue = v
                        if (kind == LevelKind.BRIGHTNESS) DeviceLevels.setBrightness(ctx, v) else DeviceLevels.setVolume(ctx, v)
                    }
                },
        ) {
            Column(Modifier.padding(start = 88.dp, top = 72.dp)) {
                Text(timeText, color = Color.White, fontFamily = Gothic, fontSize = 220.sp, fontWeight = FontWeight.Light, letterSpacing = (-6).sp, lineHeight = 220.sp, style = Lifted)
                Text(dateText, color = Color.White, fontFamily = Gothic, fontSize = 36.sp, fontWeight = FontWeight.Medium, style = Lifted)
                if (weatherText != null) {
                    Text(
                        weatherText + if (cities.size > 1) "  ›" else "",
                        color = Color.White.copy(alpha = 0.88f),
                        fontFamily = Gothic,
                        fontSize = 28.sp,
                        style = Lifted,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = cities.size > 1) { weatherVm.nextWeatherCity() }
                            .padding(vertical = 8.dp),
                    )
                }
            }

            // Status chips, top right
            Row(Modifier.align(Alignment.TopEnd).padding(top = 84.dp, end = 88.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                nextAlarm?.let { a -> GlassChip(Icons.Rounded.Alarm, alarmLabel(a, now), onClick = onOpenAlarms) }
                if (dndOn) GlassChip(Icons.Rounded.DarkMode, "Ne pas déranger", onClick = { panel = Panel.CONTROLS })
                updateVersion?.let { v -> GlassChip(Icons.Rounded.Download, "Mise à jour $v", onClick = onOpenSettings) }
            }

            // Pull tab, top centre
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .size(width = 220.dp, height = 72.dp)
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .background(FujiColors.GlassDark)
                    .clickable { panel = Panel.CONTROLS },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = FujiColors.Cream, modifier = Modifier.size(28.dp))
                Text("Contrôles", color = FujiColors.Cream, fontFamily = Gothic, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }

            // Apps button, bottom left
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 88.dp, bottom = 72.dp)
                    .height(96.dp)
                    .clip(RoundedCornerShape(48.dp))
                    .background(FujiColors.GlassDark)
                    .clickable { panel = Panel.APPS }
                    .padding(start = 28.dp, end = 36.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(Icons.Rounded.Apps, null, tint = FujiColors.Cream, modifier = Modifier.size(38.dp))
                Text("Apps", color = FujiColors.Cream, fontFamily = Gothic, fontSize = 26.sp, fontWeight = FontWeight.Medium)
            }

            AmbientCards(
                event = nextEvent,
                nowPlaying = nowPlaying,
                nextAlarm = nextAlarm,
                now = now,
                onAgenda = onOpenAgenda,
                onAlarms = onOpenAlarms,
                onJoin = { link -> ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                onPrev = { nowPlayingVm.prev() },
                onToggle = { nowPlayingVm.toggle() },
                onNext = { nowPlayingVm.next() },
                onOpenPlayer = { nowPlaying?.packageName?.let { LaunchIntentResolver.launch(ctx, it) } },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 88.dp, bottom = 72.dp),
            )

            Text(
                moment.credit,
                color = Color.White.copy(alpha = 0.6f),
                fontFamily = Gothic,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
            )
        }

        // ── Brightness / volume gauge while sliding
        hudKind?.let { k ->
            LevelHud(
                k, hudValue,
                Modifier
                    .align(if (k == LevelKind.BRIGHTNESS) Alignment.TopStart else Alignment.TopEnd)
                    .padding(horizontal = 88.dp, vertical = 170.dp),
            )
        }

        // ── Apps panel
        AnimatedVisibility(visible = panel == Panel.APPS, enter = fadeIn(), exit = fadeOut()) {
            FujiPhoto(moment, Modifier.fillMaxSize(), blurred = true)
        }
        AnimatedVisibility(
            visible = panel == Panel.APPS,
            enter = fadeIn() + slideInVertically { it / 12 },
            exit = fadeOut() + slideOutVertically { it / 12 },
        ) {
            AppsPanel(
                time = timeText,
                dateLine = listOfNotNull(dateText, weather?.let { "${it.temperatureC}°" }).joinToString(" · "),
                tiles = tiles,
                badgeCounts = badgeCounts,
                contacts = recentContacts,
                shortcuts = listOf(
                    AppsShortcut("Agenda", nextEvent?.let { "${it.start.atZone(ZoneId.systemDefault()).format(TimeFmt)} ${it.title}" } ?: "Agenda, Meet, Chat", ShortcutIcons.Agenda, onOpenAgenda),
                    AppsShortcut("Alarmes", nextAlarm?.let { alarmLabel(it, now) } ?: "Aucune alarme", ShortcutIcons.Alarms, onOpenAlarms),
                    AppsShortcut("Widgets", "Tableau de bord", ShortcutIcons.Widgets, onOpenWidgets),
                    AppsShortcut("Toutes les apps", "Rechercher", ShortcutIcons.AllApps, onOpenSearch),
                ),
                openApps = recentApps,
                freedMb = ramFreed,
                onOpenApp = { pkg -> panel = Panel.NONE; LaunchIntentResolver.launch(ctx, pkg) },
                onCloseApp = { pkg -> vm.removeRecent(pkg) },
                onCloseAll = { vm.clearRecents() },
                onLaunch = { panel = Panel.NONE; launch(it) },
                onLongPress = { quickActionsTile = it },
                onAddTile = onAddTile,
                onEditTiles = onAddTile,
                onControls = { panel = Panel.CONTROLS },
                onSettings = onOpenSettings,
                onClose = { panel = Panel.NONE },
            )
        }

        // ── Control centre, pulled from the top
        AnimatedVisibility(visible = panel == Panel.CONTROLS, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x590A0E18))
                    .clickable(remember { MutableInteractionSource() }, indication = null) { panel = Panel.NONE },
            )
        }
        AnimatedVisibility(
            visible = panel == Panel.CONTROLS,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            ControlsSheet(
                onClose = { panel = Panel.NONE },
                onDnd = { showDndDuration = true },
                onScreenSaver = {
                    panel = Panel.NONE
                    ctx.startActivity(Intent(ctx, SumiSaverActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                onScreenOff = {
                    panel = Panel.NONE
                    if (saverMode) ctx.startActivity(Intent(ctx, SumiSaverActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    else ScreenLock.lockOrRequest(ctx)
                },
            )
        }

        // ── Voice listening banner (after the "Portal" wake word)
        AnimatedVisibility(
            visible = voiceState == VoiceService.ListenState.COMMAND,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                Modifier
                    .padding(bottom = 40.dp)
                    .height(96.dp)
                    .clip(RoundedCornerShape(48.dp))
                    .background(FujiColors.GlassDark)
                    .padding(horizontal = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(Icons.Rounded.Mic, null, tint = FujiColors.Cream, modifier = Modifier.size(36.dp))
                Text("Je vous écoute…", color = FujiColors.Cream, fontFamily = Gothic, fontSize = 28.sp)
            }
        }

        if (showDndDuration) LegacyScale {
            DndDurationDialog(
                onDismiss = { showDndDuration = false },
                onPick = { millis -> DndController.enableFor(ctx, millis); showDndDuration = false },
            )
        }

        quickActionsTile?.let { qa -> LegacyScale {
            val index = tiles.indexOfFirst { it.id == qa.id }
            QuickActionsSheet(
                tile = qa,
                tileIndex = index,
                totalTiles = tiles.size,
                onDismiss = { quickActionsTile = null },
                onRemove = { vm.deleteTile(qa); quickActionsTile = null },
                onMoveUp = {
                    if (index > 0) vm.reorderTiles(tiles.toMutableList().apply { add(index - 1, removeAt(index)) })
                    quickActionsTile = null
                },
                onMoveDown = {
                    if (index in 0 until tiles.size - 1) vm.reorderTiles(tiles.toMutableList().apply { add(index + 1, removeAt(index)) })
                    quickActionsTile = null
                },
                onStartReorder = { quickActionsTile = null; onAddTile() },
            )
        } }

    }

}

private fun alarmLabel(at: LocalDateTime, now: LocalDateTime): String {
    val time = at.format(TimeFmt)
    val days = Duration.between(now.toLocalDate().atStartOfDay(), at.toLocalDate().atStartOfDay()).toDays()
    return when (days) {
        0L -> "Aujourd'hui $time"
        1L -> "Demain $time"
        else -> at.format(DateTimeFormatter.ofPattern("EEE", FR)).replaceFirstChar { it.titlecase(FR) } + " $time"
    }
}

@Composable
private fun GlassChip(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(FujiColors.GlassDark)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = FujiColors.Cream, modifier = Modifier.size(26.dp))
        Text(text, color = FujiColors.Cream, fontFamily = Gothic, fontSize = 21.sp, fontWeight = FontWeight.Medium)
    }
}

private sealed interface AmbientCard {
    data class Event(val e: CalendarEvent) : AmbientCard
    data class Music(val np: NowPlaying) : AmbientCard
    data class Wake(val at: LocalDateTime) : AmbientCard
}

/** Echo-Show-style rotating card: next meeting, music, next alarm. */
@Composable
private fun AmbientCards(
    event: CalendarEvent?,
    nowPlaying: NowPlaying?,
    nextAlarm: LocalDateTime?,
    now: LocalDateTime,
    onAgenda: () -> Unit,
    onAlarms: () -> Unit,
    onJoin: (String) -> Unit,
    onPrev: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cards = buildList {
        if (nowPlaying != null) add(AmbientCard.Music(nowPlaying))
        if (event != null) add(AmbientCard.Event(event))
        if (nextAlarm != null) add(AmbientCard.Wake(nextAlarm))
    }
    if (cards.isEmpty()) return
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(cards.size) {
        while (true) {
            delay(12_000)
            index = (index + 1) % cards.size.coerceAtLeast(1)
        }
    }
    val card = cards[index.coerceIn(0, cards.lastIndex)]

    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AnimatedContent(targetState = card, transitionSpec = { fadeIn() togetherWith fadeOut() }, contentKey = { it::class }, label = "card") { c ->
            Column(
                Modifier
                    .width(720.dp)
                    .height(if (c is AmbientCard.Music) 320.dp else 236.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(FujiColors.GlassDark)
                    .clickable {
                        when (c) {
                            is AmbientCard.Event -> onAgenda()
                            is AmbientCard.Music -> onOpenPlayer()
                            is AmbientCard.Wake -> onAlarms()
                        }
                    }
                    .padding(horizontal = 36.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                when (c) {
                    is AmbientCard.Event -> {
                        val mins = Duration.between(Instant.now(), c.e.start).toMinutes()
                        CardKicker(if (mins > 0) "AGENDA · DANS ${formatDelay(mins)}" else "AGENDA · EN COURS")
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                CardTitle(c.e.title)
                                val zone = ZoneId.systemDefault()
                                CardSub(c.e.start.atZone(zone).format(TimeFmt) + " – " + c.e.end.atZone(zone).format(TimeFmt) + (c.e.location?.let { " · $it" } ?: ""))
                            }
                            c.e.hangoutLink?.let { link -> PillAction("Rejoindre", Icons.Rounded.Videocam) { onJoin(link) } }
                        }
                    }
                    is AmbientCard.Music -> {
                        CardKicker("EN LECTURE")
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                CardTitle(c.np.title)
                                CardSub(c.np.artist)
                            }
                            RoundAction(Icons.Rounded.SkipPrevious, "Précédent", 80.dp, false, onPrev)
                            RoundAction(if (c.np.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (c.np.isPlaying) "Pause" else "Lecture", 96.dp, true, onToggle)
                            RoundAction(Icons.Rounded.SkipNext, "Suivant", 80.dp, false, onNext)
                        }
                        CardVolume(Modifier.fillMaxWidth())
                    }
                    is AmbientCard.Wake -> {
                        CardKicker("PROCHAIN RÉVEIL")
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            CardTitle(alarmLabel(c.at, now))
                            CardSub("Touchez pour gérer les alarmes")
                        }
                    }
                }
            }
        }
        if (cards.size > 1) {
            Row(Modifier.padding(end = 24.dp)) {
                cards.indices.forEach { i ->
                    val w by animateDpAsState(if (i == index) 40.dp else 10.dp, label = "dot")
                    Box(
                        Modifier.size(width = 56.dp, height = 48.dp).clickable { index = i },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(width = w, height = 10.dp).clip(CircleShape).background(Color.White.copy(alpha = if (i == index) 1f else 0.45f)))
                    }
                }
            }
        }
    }
}

private fun formatDelay(mins: Long): String = when {
    mins < 60 -> "$mins MIN"
    else -> "${mins / 60} H ${"%02d".format(mins % 60)}"
}

@Composable private fun CardKicker(text: String) =
    Text(text, color = FujiColors.Muted, fontFamily = Gothic, fontSize = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)

@Composable private fun CardTitle(text: String) =
    Text(text, color = FujiColors.Cream, fontFamily = Gothic, fontSize = 34.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable private fun CardSub(text: String) =
    Text(text, color = FujiColors.Muted, fontFamily = Gothic, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
private fun PillAction(text: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.height(88.dp).clip(RoundedCornerShape(44.dp)).background(FujiColors.Cream).clickable(onClick = onClick).padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = FujiColors.Ink, modifier = Modifier.size(30.dp))
        Text(text, color = FujiColors.Ink, fontFamily = Gothic, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, size: Dp, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(if (filled) FujiColors.Cream else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (filled) FujiColors.Ink else FujiColors.Cream, modifier = Modifier.size(size * 0.45f))
    }
}

private suspend fun fadeBrightness(window: android.view.Window, from: Float, to: Float, durationMs: Long, steps: Int) {
    repeat(steps) { i ->
        val v = from + (to - from) * (i + 1f) / steps
        window.attributes = window.attributes.apply { screenBrightness = v }
        delay(durationMs / steps)
    }
}
