package com.cyprienbrisset.fukkatsunop.ui.alarms

import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.fukkatsunop.R
import com.cyprienbrisset.fukkatsunop.ui.sumi.SectionLabel
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu

private data class Tone(val title: String, val uri: String)

@Composable
fun RingtonePicker(selectedUri: String?, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val tones = remember {
        listOf(
            Tone("Koto", "android.resource://${ctx.packageName}/${R.raw.alarm_koto}"),
        )
    }
    // Si l'URI stocké ne correspond plus à aucune sonnerie disponible, sélectionner le Koto.
    LaunchedEffect(selectedUri) {
        if (tones.none { it.uri == selectedUri }) onSelect(tones.first().uri)
    }

    var preview by remember { mutableStateOf<Ringtone?>(null) }
    DisposableEffect(Unit) { onDispose { preview?.stop() } }

    Column(modifier) {
        SectionLabel("音", "SONNERIE — TOUCHEZ POUR ÉCOUTER")
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(tones) { t ->
                val on = selectedUri == t.uri
                Row(
                    Modifier.height(60.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (on) Shu.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant)
                        .border(BorderStroke(if (on) 1.5.dp else 1.dp, if (on) Shu else MaterialTheme.colorScheme.outline), RoundedCornerShape(14.dp))
                        .clickable {
                            onSelect(t.uri)
                            preview?.stop()
                            preview = RingtoneManager.getRingtone(ctx, Uri.parse(t.uri))?.also { it.play() }
                        }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("♪", color = Shu, fontSize = 12.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(t.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                }
            }
        }
    }
}
