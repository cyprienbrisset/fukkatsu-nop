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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.cyprienbrisset.fukkatsunop.ui.sumi.SectionLabel
import com.cyprienbrisset.fukkatsunop.ui.theme.Kinari
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiLine
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiSurface

private data class Tone(val title: String, val uri: String)

@Composable
fun RingtonePicker(selectedUri: String?, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val tones = remember {
        val mgr = RingtoneManager(ctx).apply { setType(RingtoneManager.TYPE_ALARM) }
        val cur = mgr.cursor
        buildList {
            add(Tone("Par défaut", ""))
            var pos = 0
            while (cur.moveToNext()) {
                val title = runCatching { mgr.getRingtone(pos).getTitle(ctx) }.getOrNull() ?: "Sonnerie ${pos + 1}"
                val uri = mgr.getRingtoneUri(pos).toString()
                add(Tone(title, uri)); pos++
                if (pos >= 20) break
            }
        }
    }
    var preview by remember { mutableStateOf<Ringtone?>(null) }
    DisposableEffect(Unit) { onDispose { preview?.stop() } }

    Column(modifier) {
        SectionLabel("音", "SONNERIE — TOUCHEZ POUR ÉCOUTER")
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(tones) { t ->
                val on = (selectedUri ?: "") == t.uri
                Row(
                    Modifier.height(60.dp).clip(RoundedCornerShape(14.dp)).background(SumiSurface)
                        .border(BorderStroke(1.dp, if (on) Shu else SumiLine), RoundedCornerShape(14.dp))
                        .clickable {
                            onSelect(if (t.uri.isEmpty()) null else t.uri)
                            preview?.stop()
                            val uri = if (t.uri.isEmpty()) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) else Uri.parse(t.uri)
                            preview = RingtoneManager.getRingtone(ctx, uri)?.also { it.play() }
                        }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("▶", color = Shu, fontSize = 12.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(t.title, color = Kinari, fontSize = 14.sp)
                }
            }
        }
    }
}
