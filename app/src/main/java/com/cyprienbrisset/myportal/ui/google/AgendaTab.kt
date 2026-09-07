package com.cyprienbrisset.myportal.ui.google

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.myportal.integration.google.CalendarEvent
import com.cyprienbrisset.myportal.ui.sumi.SectionLabel
import com.cyprienbrisset.myportal.ui.sumi.SumiPrimaryButton
import com.cyprienbrisset.myportal.ui.theme.Kinari
import com.cyprienbrisset.myportal.ui.theme.Mincho
import com.cyprienbrisset.myportal.ui.theme.Shu
import com.cyprienbrisset.myportal.ui.theme.SumiMuted
import com.cyprienbrisset.myportal.ui.theme.SumiSurface
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// Fix 3 — top-level formatters (never recreated on recomposition)
private val DATE_FMT = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.FRENCH)
private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun AgendaTab(state: TabState<List<CalendarEvent>>, onRetry: () -> Unit) {
    when (state) {
        is TabState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = Shu)
        }
        is TabState.Error -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message, color = SumiMuted, fontFamily = Mincho, fontSize = 15.sp)
                Spacer(Modifier.height(16.dp))
                SumiPrimaryButton("Réessayer", onRetry, Modifier.fillMaxWidth(0.5f))
            }
        }
        is TabState.Success -> {
            if (state.data.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Aucun événement à venir", color = SumiMuted, fontFamily = Mincho)
                }
                return
            }
            val zone = ZoneId.systemDefault()
            val today    = LocalDate.now(zone)
            val tomorrow = today.plusDays(1)
            val grouped  = state.data.groupBy { it.start.atZone(zone).toLocalDate() }
            // Fix 2 — compute once, stable across recompositions
            val now = remember { Instant.now() }

            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                grouped.forEach { (date, events) ->
                    val label = when (date) {
                        today    -> "Aujourd'hui"
                        tomorrow -> "Demain"
                        // Fix 3 — use top-level DATE_FMT
                        else     -> date.format(DATE_FMT)
                    }
                    // Fix 4 — stable key; Fix 5 — explicit locale for uppercase
                    item(key = "header-$date") {
                        Spacer(Modifier.height(20.dp))
                        SectionLabel(kana = "", text = label.uppercase(Locale.FRENCH))
                        Spacer(Modifier.height(8.dp))
                    }
                    items(events, key = { it.id }) { event ->
                        // Fix 2 — pass hoisted now
                        EventCard(event = event, zone = zone, now = now)
                        Spacer(Modifier.height(8.dp))
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

// Fix 2 — now is passed in (hoisted in AgendaTab) instead of computed here
@Composable
private fun EventCard(event: CalendarEvent, zone: ZoneId, now: Instant) {
    val ctx = LocalContext.current
    val isImminent = event.start.isAfter(now) && event.start.isBefore(now.plusSeconds(3600))
    val accentColor = if (isImminent) Shu else SumiSurface

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accentColor),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = event.title,
                fontFamily = Mincho,
                fontSize = 15.sp,
                color = Kinari,
                fontStyle = if (event.isAllDay) FontStyle.Italic else FontStyle.Normal,
            )
            Spacer(Modifier.height(2.dp))
            // Fix 1+3 — use top-level TIME_FMT (no Locale needed for HH:mm)
            val timeStr = if (event.isAllDay) "Journée entière"
                else "${event.start.atZone(zone).format(TIME_FMT)} – ${event.end.atZone(zone).format(TIME_FMT)}"
            Text(timeStr, style = MaterialTheme.typography.bodyMedium, color = SumiMuted)
            if (event.location != null) {
                Text("📍 ${event.location}", style = MaterialTheme.typography.bodyMedium, color = SumiMuted)
            }
        }
        if (event.hangoutLink != null) {
            Spacer(Modifier.width(8.dp))
            SumiPrimaryButton(
                text = "Rejoindre",
                onClick = {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(event.hangoutLink))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                modifier = Modifier.size(width = 110.dp, height = 44.dp),
            )
        }
    }
}
