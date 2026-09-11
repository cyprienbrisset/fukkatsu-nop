package com.cyprienbrisset.fukkatsunop.ui.alarms

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyprienbrisset.fukkatsunop.ui.sumi.HankoSeal
import com.cyprienbrisset.fukkatsunop.ui.theme.Kinari
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho
import com.cyprienbrisset.fukkatsunop.ui.theme.AccentShu
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiLine
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiMuted

@Composable
fun AlarmsScreen(onBack: () -> Unit, onAdd: () -> Unit, onEdit: (Long) -> Unit = {}, vm: AlarmsViewModel = viewModel()) {
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 32.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            HankoSeal("鈴", size = 40.dp, onClick = onBack)
            Spacer(Modifier.width(14.dp))
            Text("Alarmes", fontFamily = Mincho, color = Kinari, fontSize = 22.sp)
            Spacer(Modifier.weight(1f))
            HankoSeal("＋", size = 44.dp, onClick = onAdd)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            items(alarms, key = { it.id }) { a ->
                Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable { onEdit(a.id) }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("%02d:%02d".format(a.hour, a.minute), fontFamily = Mincho, color = Kinari, fontSize = 30.sp)
                        Text((if (a.repeatDays == 0) "Une fois" else repeatLabel(a.repeatDays)) + " · Snooze ${a.snoozeMinutes}m", color = SumiMuted, fontSize = 13.sp)
                    }
                    Switch(checked = a.enabled, onCheckedChange = { vm.toggle(a, it) })
                    Spacer(Modifier.width(8.dp))
                    Text("✕", color = AccentShu, fontSize = 20.sp, modifier = Modifier.padding(8.dp).clickable(onClick = { vm.delete(a) }))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(SumiLine))
            }
        }
    }
}

private fun repeatLabel(mask: Int): String {
    val l = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")
    return l.filterIndexed { i, _ -> (mask and (1 shl i)) != 0 }.joinToString(" ")
}
