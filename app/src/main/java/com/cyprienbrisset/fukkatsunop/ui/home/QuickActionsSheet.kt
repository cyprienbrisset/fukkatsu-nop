package com.cyprienbrisset.fukkatsunop.ui.home

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Process
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.fukkatsunop.data.tile.TileEntity
import com.cyprienbrisset.fukkatsunop.data.tile.TileType
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionsSheet(
    tile: TileEntity,
    tileIndex: Int,
    totalTiles: Int,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onStartReorder: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val sheetState = rememberModalBottomSheetState()

    val shortcuts: List<ShortcutInfo> = remember(tile.packageName) {
        if (tile.type != TileType.APP || tile.packageName == null) return@remember emptyList()
        val la = ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            ?: return@remember emptyList()
        val q = LauncherApps.ShortcutQuery()
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST,
            )
            .setPackage(tile.packageName)
        runCatching { la.getShortcuts(q, Process.myUserHandle()) ?: emptyList() }
            .getOrDefault(emptyList())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxWidth(0.6f),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text(
                tile.label,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            shortcuts.take(4).forEach { shortcut ->
                val label = shortcut.shortLabel?.toString()
                    ?: shortcut.longLabel?.toString()
                    ?: return@forEach
                QaRow(label = label) {
                    val la = ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
                    runCatching { la?.startShortcut(shortcut, null, null) }
                    onDismiss()
                }
            }
            if (shortcuts.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
            }

            if (tileIndex > 0) {
                QaRow(
                    icon = { Icon(Icons.Rounded.ArrowUpward, null, tint = MaterialTheme.colorScheme.onBackground) },
                    label = "Déplacer vers le haut",
                ) { onMoveUp(); onDismiss() }
            }

            if (tileIndex < totalTiles - 1) {
                QaRow(
                    icon = { Icon(Icons.Rounded.ArrowDownward, null, tint = MaterialTheme.colorScheme.onBackground) },
                    label = "Déplacer vers le bas",
                ) { onMoveDown(); onDismiss() }
            }

            if (onStartReorder != null) {
                QaRow(
                    icon = { Icon(Icons.Rounded.SwapVert, null, tint = MaterialTheme.colorScheme.onBackground) },
                    label = "Réorganiser les tuiles",
                ) { onStartReorder(); onDismiss() }
            }

            QaRow(
                icon = { Icon(Icons.Rounded.Delete, null, tint = Shu) },
                label = "Retirer la tuile",
                labelColor = Shu,
            ) { onRemove(); onDismiss() }
        }
    }
}

@Composable
private fun QaRow(
    icon: (@Composable () -> Unit)? = null,
    label: String,
    labelColor: Color = MaterialTheme.colorScheme.onBackground,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) icon()
        Text(label, color = labelColor, fontSize = 15.sp)
    }
}
