package com.cyprienbrisset.fukkatsunop.ui.alarms

import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyprienbrisset.fukkatsunop.R
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiButton
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiColors
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiLabel
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiRow
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiScreen
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiSegmented
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiSwitch
import com.cyprienbrisset.fukkatsunop.ui.fuji.glass
import com.cyprienbrisset.fukkatsunop.ui.home.FujiSlider
import com.cyprienbrisset.fukkatsunop.ui.sumi.stepHour
import com.cyprienbrisset.fukkatsunop.ui.sumi.stepMinute
import com.cyprienbrisset.fukkatsunop.ui.theme.Gothic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp

private data class Tone(val title: String, val uri: String)

@Composable
fun AlarmEditScreen(onDone: () -> Unit, alarmId: Long = 0L, vm: AlarmsViewModel = viewModel()) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var hour by remember { mutableIntStateOf(7) }
    var minute by remember { mutableIntStateOf(0) }
    var days by remember { mutableIntStateOf(0) }
    var label by remember { mutableStateOf("") }
    var snooze by remember { mutableIntStateOf(10) }
    var ringtoneUri by remember { mutableStateOf<String?>(null) }
    var videoEnabled by remember { mutableStateOf(true) }
    var volumeProgressive by remember { mutableStateOf(true) }
    var volumeLevel by remember { mutableIntStateOf(100) }

    val tones = remember {
        listOf(
            Tone("Koto", "android.resource://${ctx.packageName}/${R.raw.alarm_koto}"),
            Tone("Japan", "android.resource://${ctx.packageName}/${R.raw.alarm_japan}"),
            Tone("Anime", "android.resource://${ctx.packageName}/${R.raw.alarm_anime}"),
        )
    }
    var preview by remember { mutableStateOf<Ringtone?>(null) }
    DisposableEffect(Unit) { onDispose { preview?.stop() } }

    LaunchedEffect(alarmId) {
        if (alarmId > 0L) {
            vm.getById(alarmId)?.let { a ->
                hour = a.hour; minute = a.minute; days = a.repeatDays; label = a.label
                snooze = a.snoozeMinutes; ringtoneUri = a.ringtoneUri; videoEnabled = a.videoEnabled
                volumeProgressive = a.volumeProgressive; volumeLevel = a.volumeLevel
            }
        }
        if (tones.none { it.uri == ringtoneUri }) ringtoneUri = tones.first().uri
    }

    FujiScreen(
        title = if (alarmId > 0L) "Modifier l'alarme" else "Nouvelle alarme",
        onBack = onDone,
        actions = {
            if (alarmId > 0L) {
                FujiButton("Supprimer", {
                    scope.launch {
                        vm.getById(alarmId)?.let { vm.delete(it) }
                        onDone()
                    }
                }, icon = Icons.Rounded.Delete, height = 80.dp)
            }
            FujiButton("Enregistrer", {
                vm.save(hour, minute, days, label, ringtoneUri, snooze, videoEnabled, volumeProgressive, volumeLevel, id = alarmId)
                onDone()
            }, primary = true)
        },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            // ── Time: big steppers, no tiny wheels
            Row(
                Modifier
                    .width(760.dp)
                    .fillMaxHeight()
                    .glass(RoundedCornerShape(40.dp), FujiColors.Glass),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimeStepper("%02d".format(hour), "heure", { hour = stepHour(hour, 1) }, { hour = stepHour(hour, -1) })
                Text(":", color = FujiColors.Cream, fontFamily = Gothic, fontSize = 180.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(horizontal = 24.dp, vertical = 0.dp))
                TimeStepper("%02d".format(minute), "minutes", { minute = stepMinute(minute, 5) }, { minute = stepMinute(minute, -5) }, onFineUp = { minute = stepMinute(minute, 1) })
            }

            // ── Options
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(30.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    FujiLabel("Répéter · ${repeatLabel(days)}")
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        listOf("L", "M", "M", "J", "V", "S", "D").forEachIndexed { i, d ->
                            val on = days and (1 shl i) != 0
                            Box(
                                Modifier
                                    .size(100.dp)
                                    .clip(CircleShape)
                                    .background(if (on) FujiColors.Cream else FujiColors.GlassStrong)
                                    .clickable(role = Role.Checkbox) { days = days xor (1 shl i) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(d, color = if (on) FujiColors.Ink else FujiColors.Cream, fontFamily = Gothic, fontSize = 28.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    FujiLabel("Sonnerie · touchez pour écouter")
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        tones.forEach { t ->
                            val on = ringtoneUri == t.uri
                            FujiButton(
                                t.title,
                                {
                                    ringtoneUri = t.uri
                                    preview?.stop()
                                    preview = RingtoneManager.getRingtone(ctx, Uri.parse(t.uri))?.also { it.play() }
                                },
                                Modifier.weight(1f),
                                primary = on,
                                icon = Icons.Rounded.PlayArrow,
                                height = 96.dp,
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    FujiLabel("Snooze")
                    FujiSegmented(listOf("5 min", "10 min", "15 min"), listOf(5, 10, 15).indexOf(snooze).coerceAtLeast(0), { snooze = listOf(5, 10, 15)[it] })
                }

                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    FujiLabel("Montée du volume")
                    FujiSegmented(listOf("Progressive (30 s)", "Volume fixe"), if (volumeProgressive) 0 else 1, { volumeProgressive = it == 0 })
                    if (!volumeProgressive) {
                        FujiSlider("Volume au réveil", Icons.AutoMirrored.Rounded.VolumeUp, volumeLevel / 100f) { f ->
                            volumeLevel = (f * 100).toInt().coerceIn(10, 100)
                        }
                    }
                }

                FujiRow(
                    title = "Lever de soleil",
                    subtitle = "L'écran s'éclaire doucement avec une vidéo avant la sonnerie",
                    onClick = { videoEnabled = !videoEnabled },
                ) { FujiSwitch(videoEnabled, { videoEnabled = it }) }
            }
        }
    }
}

@Composable
private fun TimeStepper(value: String, unit: String, onUp: () -> Unit, onDown: () -> Unit, onFineUp: (() -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StepButton(Icons.Rounded.KeyboardArrowUp, "Augmenter les $unit", onUp)
        Text(
            value,
            color = FujiColors.Cream,
            fontFamily = Gothic,
            fontSize = 200.sp,
            fontWeight = FontWeight.Light,
            modifier = if (onFineUp != null) Modifier.clickable(onClickLabel = "Plus une minute", onClick = onFineUp) else Modifier,
        )
        StepButton(Icons.Rounded.KeyboardArrowDown, "Diminuer les $unit", onDown)
    }
}

@Composable
private fun StepButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 200.dp, height = 96.dp)
            .clip(RoundedCornerShape(48.dp))
            .background(FujiColors.GlassStrong)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = FujiColors.Cream, modifier = Modifier.size(48.dp))
    }
}
