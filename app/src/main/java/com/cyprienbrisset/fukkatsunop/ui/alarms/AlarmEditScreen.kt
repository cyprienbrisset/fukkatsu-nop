package com.cyprienbrisset.fukkatsunop.ui.alarms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyprienbrisset.fukkatsunop.ui.sumi.HankoSeal
import com.cyprienbrisset.fukkatsunop.ui.sumi.SectionLabel
import com.cyprienbrisset.fukkatsunop.ui.sumi.Stepper
import com.cyprienbrisset.fukkatsunop.ui.sumi.SumiChoiceChip
import com.cyprienbrisset.fukkatsunop.ui.sumi.SumiPrimaryButton
import com.cyprienbrisset.fukkatsunop.ui.sumi.stepHour
import com.cyprienbrisset.fukkatsunop.ui.sumi.stepMinute
import com.cyprienbrisset.fukkatsunop.ui.theme.Kinari
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiMuted

@Composable
fun AlarmEditScreen(onDone: () -> Unit, vm: AlarmsViewModel = viewModel()) {
    var hour by remember { mutableStateOf(7) }
    var minute by remember { mutableStateOf(0) }
    var days by remember { mutableStateOf(0) }
    var snooze by remember { mutableStateOf(10) }
    var ringtoneUri by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 32.dp).padding(top = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HankoSeal("鈴", size = 40.dp, onClick = onDone)
            Spacer(Modifier.width(14.dp))
            Text("Nouvelle alarme", fontFamily = Mincho, color = Kinari, fontSize = 22.sp)
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Stepper(hour, onUp = { hour = stepHour(hour, +1) }, onDown = { hour = stepHour(hour, -1) })
            Text(":", color = SumiMuted, fontFamily = Mincho, fontSize = 60.sp, modifier = Modifier.padding(horizontal = 8.dp))
            Stepper(minute, onUp = { minute = stepMinute(minute, +1) }, onDown = { minute = stepMinute(minute, -1) })
        }
        Spacer(Modifier.height(28.dp))
        SectionLabel("繰り返し", "RÉPÉTER")
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("L", "M", "M", "J", "V", "S", "D").forEachIndexed { i, d ->
                SumiChoiceChip(d, selected = (days and (1 shl i)) != 0, circle = true, onClick = { days = days xor (1 shl i) }, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(24.dp))
        SectionLabel("スヌーズ", "SNOOZE")
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(5, 10, 15).forEach { m ->
                SumiChoiceChip("$m min", selected = snooze == m, onClick = { snooze = m }, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(24.dp))
        RingtonePicker(selectedUri = ringtoneUri, onSelect = { ringtoneUri = it })
        Spacer(Modifier.weight(1f))
        SumiPrimaryButton("保存 · Enregistrer", onClick = { vm.save(hour, minute, days, "", ringtoneUri, snooze); onDone() })
        Spacer(Modifier.height(20.dp))
    }
}
