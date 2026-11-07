package com.cyprienbrisset.fukkatsunop.ui.alarms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyprienbrisset.fukkatsunop.data.alarm.AlarmEntity
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiButton
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiColors
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiScreen
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiSwitch
import com.cyprienbrisset.fukkatsunop.ui.fuji.glass
import com.cyprienbrisset.fukkatsunop.ui.theme.Gothic

private val DAY_NAMES = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")

/** Human summary of the repeat bitmask (bit 0 = Monday). */
fun repeatLabel(mask: Int): String = when (mask) {
    0 -> "Une fois"
    0b1111111 -> "Tous les jours"
    0b0011111 -> "Lun – Ven"
    0b1100000 -> "Sam, Dim"
    else -> DAY_NAMES.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.joinToString(", ")
}

@Composable
fun AlarmsScreen(onBack: () -> Unit, onAdd: () -> Unit, onEdit: (Long) -> Unit = {}, vm: AlarmsViewModel = viewModel()) {
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    val active = alarms.count { it.enabled }

    FujiScreen(
        title = "Alarmes",
        onBack = onBack,
        actions = {
            if (alarms.isNotEmpty()) {
                Text(
                    "$active active${if (active > 1) "s" else ""}",
                    color = FujiColors.Muted, fontFamily = Gothic, fontSize = 22.sp,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
            FujiButton("Nouvelle alarme", onAdd, primary = true, icon = Icons.Rounded.Add)
        },
    ) {
        if (alarms.isEmpty()) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("Aucune alarme", color = FujiColors.Cream, fontFamily = Gothic, fontSize = 40.sp, fontWeight = FontWeight.Medium)
                Text("Réveillez-vous avec le lever de soleil sur le Fuji.", color = FujiColors.Muted, fontFamily = Gothic, fontSize = 24.sp)
                FujiButton("Créer une alarme", onAdd, primary = true, icon = Icons.Rounded.Add, height = 96.dp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                items(alarms, key = { it.id }) { a ->
                    AlarmCard(a, onClick = { onEdit(a.id) }, onToggle = { vm.toggle(a, it) })
                }
            }
        }
    }
}

@Composable
private fun AlarmCard(a: AlarmEntity, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .glass(RoundedCornerShape(36.dp), if (a.enabled) FujiColors.GlassStrong else FujiColors.Glass)
            .padding(start = 44.dp, end = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column(
            Modifier.weight(1f).clickable(onClick = onClick).alpha(if (a.enabled) 1f else 0.55f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("%02d:%02d".format(a.hour, a.minute), color = FujiColors.Cream, fontFamily = Gothic, fontSize = 96.sp, fontWeight = FontWeight.Light, lineHeight = 100.sp)
            Text(
                listOf(a.label.ifBlank { "Réveil" }, repeatLabel(a.repeatDays)).joinToString(" · "),
                color = FujiColors.Cream, fontFamily = Gothic, fontSize = 23.sp,
            )
            Text(
                listOf(
                    if (a.videoEnabled) "lever de soleil" else "sans vidéo",
                    if (a.volumeProgressive) "montée progressive" else "volume ${a.volumeLevel} %",
                    "snooze ${a.snoozeMinutes} min",
                ).joinToString(" · "),
                color = FujiColors.Muted, fontFamily = Gothic, fontSize = 19.sp,
            )
        }
        Box { FujiSwitch(a.enabled, onToggle) }
    }
}
