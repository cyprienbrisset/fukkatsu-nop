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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.myportal.integration.google.CalendarEvent
import com.cyprienbrisset.myportal.ui.sumi.SumiPrimaryButton
import com.cyprienbrisset.myportal.ui.theme.Gothic
import com.cyprienbrisset.myportal.ui.theme.Kinari
import com.cyprienbrisset.myportal.ui.theme.Mincho
import com.cyprienbrisset.myportal.ui.theme.Shu
import com.cyprienbrisset.myportal.ui.theme.SumiMuted
import com.cyprienbrisset.myportal.ui.theme.SumiSurface
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val MEET_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun MeetTab(state: TabState<List<CalendarEvent>>, onRetry: () -> Unit) {
    val now = remember { Instant.now() }
    val meetEvents = remember(state, now) {
        if (state is TabState.Success) {
            state.data
                .filter {
                    it.hangoutLink != null &&
                        it.start.isBefore(now.plusSeconds(48 * 3600)) &&
                        it.end.isAfter(now)
                }
                .sortedBy { it.start }
        } else emptyList()
    }

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
            if (meetEvents.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Aucune réunion à venir", color = SumiMuted, fontFamily = Mincho)
                }
                return
            }
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                item { Spacer(Modifier.height(16.dp)) }
                items(meetEvents, key = { it.id }) { event ->
                    MeetCard(event = event, now = now)
                    Spacer(Modifier.height(12.dp))
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun MeetCard(event: CalendarEvent, now: Instant) {
    val ctx = LocalContext.current
    val zone = ZoneId.systemDefault()

    val countdownLabel: String = if (event.start.isBefore(now)) {
        "EN COURS"
    } else {
        val secondsUntil = event.start.epochSecond - now.epochSecond
        val hours = secondsUntil / 3600
        val minutes = (secondsUntil % 3600) / 60
        if (hours > 0) "DANS ${hours}h ${minutes}min" else "DANS ${minutes}min"
    }

    val startStr = event.start.atZone(zone).format(MEET_TIME_FMT)
    val endStr = event.end.atZone(zone).format(MEET_TIME_FMT)
    val timeRange = "$startStr – $endStr"

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SumiSurface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = countdownLabel,
            color = Shu,
            fontFamily = Gothic,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = event.title,
            color = Kinari,
            fontFamily = Mincho,
            fontSize = 18.sp,
        )
        Text(
            text = timeRange,
            color = SumiMuted,
            fontFamily = Gothic,
            fontSize = 13.sp,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            SumiPrimaryButton(
                text = "Rejoindre",
                onClick = {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(event.hangoutLink!!))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                modifier = Modifier.fillMaxWidth(0.5f),
            )
        }
    }
}
