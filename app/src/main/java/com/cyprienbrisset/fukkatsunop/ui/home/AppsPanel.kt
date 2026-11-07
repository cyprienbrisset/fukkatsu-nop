package com.cyprienbrisset.fukkatsunop.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import com.cyprienbrisset.fukkatsunop.integration.RecentApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.fukkatsunop.airplay.AirPlayReceiver
import com.cyprienbrisset.fukkatsunop.data.tile.TileEntity
import com.cyprienbrisset.fukkatsunop.integration.RecentContact
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiBadge
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiButton
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiColors
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiIconButton
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiLabel
import com.cyprienbrisset.fukkatsunop.ui.fuji.glass
import com.cyprienbrisset.fukkatsunop.ui.theme.Gothic

data class AppsShortcut(val label: String, val sub: String, val icon: ImageVector, val onClick: () -> Unit)

/** Full-screen app launcher shown over the ambient photo when the screen is touched. */
@Composable
fun AppsPanel(
    time: String,
    dateLine: String,
    tiles: List<TileEntity>,
    badgeCounts: Map<String, Int>,
    contacts: List<RecentContact>,
    shortcuts: List<AppsShortcut>,
    openApps: List<RecentApp>,
    freedMb: Long?,
    onOpenApp: (String) -> Unit,
    onCloseApp: (String) -> Unit,
    onCloseAll: () -> Unit,
    onLaunch: (TileEntity) -> Unit,
    onLongPress: (TileEntity) -> Unit,
    onAddTile: () -> Unit,
    onEditTiles: () -> Unit,
    onControls: () -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val airPlayState by AirPlayReceiver.state.collectAsState()
    Column(
        modifier
            .fillMaxSize()
            .background(FujiColors.Veil)
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .padding(start = 88.dp, end = 88.dp, top = 56.dp, bottom = 56.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(time, color = FujiColors.Cream, fontFamily = Gothic, fontSize = 56.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.size(4.dp))
            Text(dateLine, color = FujiColors.Muted, fontFamily = Gothic, fontSize = 24.sp, modifier = Modifier.weight(1f), maxLines = 1)
            FujiButton("Contrôles", onControls, icon = Icons.Rounded.Tune, height = 76.dp)
            FujiButton("Réglages", onSettings, icon = Icons.Rounded.Settings, height = 76.dp)
            FujiIconButton(Icons.Rounded.Close, "Fermer", onClose, size = 76.dp, filled = true)
        }

        Row(Modifier.padding(top = 48.dp, bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            FujiLabel("Favoris", Modifier.weight(1f))
            FujiButton("Modifier", onEditTiles, icon = Icons.Rounded.Edit, height = 64.dp)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(36.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
        ) {
            items(tiles, key = { it.id }) { tile ->
                AppTile(
                    tile = tile,
                    badge = tile.packageName?.let { badgeCounts[it] } ?: 0,
                    airPlayState = airPlayState,
                    onClick = { onLaunch(tile) },
                    onLongClick = { onLongPress(tile) },
                )
            }
            item(key = "__add__") { AddTile(onAddTile) }
        }

        if (contacts.isNotEmpty()) {
            Row(Modifier.padding(top = 12.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                FujiLabel("Contacts récents")
                RecentContactsStrip(contacts, Modifier.weight(1f))
            }
        }

        if (openApps.isNotEmpty() || freedMb != null) {
            OpenAppsRow(openApps, freedMb, onOpenApp, onCloseApp, onCloseAll, Modifier.padding(bottom = 24.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            shortcuts.forEach { s -> ShortcutCard(s, Modifier.weight(1f)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(
    tile: TileEntity,
    badge: Int,
    airPlayState: com.cyprienbrisset.fukkatsunop.airplay.AirPlayState,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box {
            TileIcon(tile = tile, size = 140.dp, airPlayState = airPlayState)
            if (badge > 0) FujiBadge(badge, Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-10).dp))
        }
        Text(tile.label, color = FujiColors.Cream, fontFamily = Gothic, fontSize = 23.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(38.dp))
                .border(3.dp, FujiColors.Faint, RoundedCornerShape(38.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, null, tint = FujiColors.Cream, modifier = Modifier.size(56.dp))
        }
        Text("Ajouter", color = FujiColors.Cream, fontFamily = Gothic, fontSize = 23.sp)
    }
}

@Composable
private fun ShortcutCard(s: AppsShortcut, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(128.dp)
            .glass(RoundedCornerShape(32.dp), FujiColors.Glass)
            .clickable(onClick = s.onClick)
            .padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Box(Modifier.size(68.dp).clip(CircleShape).background(FujiColors.GlassStrong), contentAlignment = Alignment.Center) {
            Icon(s.icon, null, tint = FujiColors.Cream, modifier = Modifier.size(34.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(s.label, color = FujiColors.Cream, fontFamily = Gothic, fontSize = 26.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(s.sub, color = FujiColors.Muted, fontFamily = Gothic, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Apps opened and not closed yet: tap to return, cross to close. */
@Composable
private fun OpenAppsRow(
    apps: List<RecentApp>,
    freedMb: Long?,
    onOpen: (String) -> Unit,
    onClose: (String) -> Unit,
    onCloseAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(32.dp), FujiColors.Glass)
            .padding(start = 32.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(Modifier.width(210.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Apps ouvertes", color = FujiColors.Cream, fontFamily = Gothic, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Text(
                when {
                    apps.isEmpty() && freedMb != null -> "$freedMb Mo libérés"
                    apps.isEmpty() -> "Aucune"
                    else -> "${apps.size} en mémoire"
                },
                color = FujiColors.Muted, fontFamily = Gothic, fontSize = 19.sp,
            )
        }
        LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(apps, key = { it.packageName }) { app -> OpenAppChip(app, onOpen, onClose) }
        }
        if (apps.isNotEmpty()) FujiButton("Tout fermer", onCloseAll, icon = Icons.Rounded.ClearAll, height = 80.dp)
    }
}

@Composable
private fun OpenAppChip(app: RecentApp, onOpen: (String) -> Unit, onClose: (String) -> Unit) {
    val ctx = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { ctx.packageManager.getApplicationIcon(app.packageName).toBitmap(128, 128).asImageBitmap() }.getOrNull()
        }
    }
    Row(
        Modifier
            .height(88.dp)
            .glass(RoundedCornerShape(44.dp), FujiColors.GlassStrong)
            .clickable { onOpen(app.packageName) }
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp))) {
            icon?.let { Image(it, null, Modifier.fillMaxSize()) }
        }
        Text(app.label, color = FujiColors.Cream, fontFamily = Gothic, fontSize = 21.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(FujiColors.GlassStrong).clickable(onClickLabel = "Fermer ${app.label}") { onClose(app.packageName) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, "Fermer ${app.label}", tint = FujiColors.Cream, modifier = Modifier.size(30.dp))
        }
    }
}

/** Icons for the four shortcut cards, kept here so HomeShell stays readable. */
object ShortcutIcons {
    val Agenda = Icons.Rounded.CalendarMonth
    val Alarms = Icons.Rounded.Alarm
    val Widgets = Icons.Rounded.Dashboard
    val AllApps = Icons.Rounded.Search
}
