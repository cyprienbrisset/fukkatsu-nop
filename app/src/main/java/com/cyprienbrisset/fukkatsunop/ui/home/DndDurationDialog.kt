package com.cyprienbrisset.fukkatsunop.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cyprienbrisset.fukkatsunop.system.millisUntilHour
import com.cyprienbrisset.fukkatsunop.ui.sumi.Stepper
import com.cyprienbrisset.fukkatsunop.ui.sumi.SumiChoiceChip
import java.time.LocalDateTime

@Composable
fun DndDurationDialog(onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    var custom by remember { mutableStateOf(false) }
    var hours by remember { mutableIntStateOf(2) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.6f),
        containerColor = MaterialTheme.colorScheme.surface,
        confirmButton = {
            if (custom) TextButton(onClick = { onPick(hours * 60L * 60L * 1000L) }) { Text("Activer ${hours}h") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        title = { Text("Ne pas déranger") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SumiChoiceChip("1 heure", selected = false, onClick = { onPick(60L * 60L * 1000L) }, modifier = Modifier.weight(1f))
                    SumiChoiceChip("Jusqu'à 8h", selected = false, onClick = { onPick(millisUntilHour(LocalDateTime.now(), 8)) }, modifier = Modifier.weight(1f))
                    SumiChoiceChip("Perso", selected = custom, onClick = { custom = true }, modifier = Modifier.weight(1f))
                }
                if (custom) {
                    Spacer(Modifier.height(16.dp))
                    Stepper(hours, onUp = { if (hours < 12) hours++ }, onDown = { if (hours > 1) hours-- })
                }
            }
        },
    )
}
