package com.cyprienbrisset.fukkatsunop.ui.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cyprienbrisset.fukkatsunop.system.DarkModeManager
import com.cyprienbrisset.fukkatsunop.ui.sumi.HankoSeal
import com.cyprienbrisset.fukkatsunop.ui.theme.Kinari
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DarkModeScheduleScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var schedule by remember { mutableStateOf(DarkModeManager.getSchedule(ctx)) }

    // Which time picker is open: "dark" | "light" | null
    var pickerFor by remember { mutableStateOf<String?>(null) }

    val darkPickerState = rememberTimePickerState(
        initialHour = schedule.darkHour,
        initialMinute = schedule.darkMin,
        is24Hour = true,
    )
    val lightPickerState = rememberTimePickerState(
        initialHour = schedule.lightHour,
        initialMinute = schedule.lightMin,
        is24Hour = true,
    )

    fun save(new: DarkModeManager.Schedule) {
        schedule = new
        DarkModeManager.saveSchedule(ctx, new)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 32.dp),
    ) {
        // Header
        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            HankoSeal("朱", size = 40.dp, onClick = onBack)
            Spacer(Modifier.width(14.dp))
            Text("Mode nuit auto", fontFamily = Mincho, color = MaterialTheme.colorScheme.onBackground, fontSize = 22.sp)
        }

        // Enable toggle
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Basculement automatique", fontFamily = Mincho, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("Passe en mode nuit et jour à l'heure définie", fontFamily = Mincho, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 12.sp)
            }
            Switch(
                checked = schedule.enabled,
                onCheckedChange = { save(schedule.copy(enabled = it)) },
                colors = SwitchDefaults.colors(checkedThumbColor = Shu, checkedTrackColor = Shu.copy(alpha = 0.35f)),
            )
        }

        Spacer(Modifier.height(24.dp))

        if (schedule.enabled) {
            // Dark mode start
            TimeRow(
                icon = { Icon(Icons.Rounded.DarkMode, null, tint = Color(0xFF8A8AFF), modifier = Modifier.size(22.dp)) },
                label = "Début mode nuit",
                hour = schedule.darkHour,
                minute = schedule.darkMin,
                onClick = { pickerFor = "dark" },
            )

            Spacer(Modifier.height(12.dp))

            // Light mode start
            TimeRow(
                icon = { Icon(Icons.Rounded.LightMode, null, tint = Color(0xFFFFCC00), modifier = Modifier.size(22.dp)) },
                label = "Début mode jour",
                hour = schedule.lightHour,
                minute = schedule.lightMin,
                onClick = { pickerFor = "light" },
            )

            Spacer(Modifier.height(28.dp))

            // Visual summary
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0A0A12))
                    .padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF8A8AFF)))
                Text(
                    "Mode nuit de %02d:%02d à %02d:%02d".format(schedule.darkHour, schedule.darkMin, schedule.lightHour, schedule.lightMin),
                    fontFamily = Mincho,
                    color = Kinari.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                )
            }
        }
    }

    // Time picker dialog
    val which = pickerFor
    if (which != null) {
        val state = if (which == "dark") darkPickerState else lightPickerState
        Dialog(
            onDismissRequest = { pickerFor = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column(
                Modifier
                    .fillMaxWidth(0.6f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (which == "dark") "Heure du mode nuit" else "Heure du mode jour",
                    fontFamily = Mincho,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 20.dp),
                )
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = MaterialTheme.colorScheme.surfaceVariant,
                        selectorColor = Shu,
                        containerColor = MaterialTheme.colorScheme.surface,
                        timeSelectorSelectedContainerColor = Shu.copy(alpha = 0.2f),
                        timeSelectorSelectedContentColor = Shu,
                    ),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { pickerFor = null }) {
                        Text("Annuler", fontFamily = Mincho, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                    TextButton(onClick = {
                        val new = if (which == "dark")
                            schedule.copy(darkHour = state.hour, darkMin = state.minute)
                        else
                            schedule.copy(lightHour = state.hour, lightMin = state.minute)
                        save(new)
                        pickerFor = null
                    }) {
                        Text("OK", fontFamily = Mincho, color = Shu, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeRow(
    icon: @Composable () -> Unit,
    label: String,
    hour: Int,
    minute: Int,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        icon()
        Text(label, fontFamily = Mincho, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(
            "%02d:%02d".format(hour, minute),
            fontFamily = Mincho,
            color = Shu,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
