package com.cyprienbrisset.fukkatsunop.ui.google

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.fukkatsunop.integration.google.CalendarEvent
import com.cyprienbrisset.fukkatsunop.ui.sumi.SumiPrimaryButton
import com.cyprienbrisset.fukkatsunop.ui.theme.AccentShu
import com.cyprienbrisset.fukkatsunop.ui.theme.OnShu
import com.cyprienbrisset.fukkatsunop.ui.theme.Gothic
import com.cyprienbrisset.fukkatsunop.ui.theme.Kinari
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiMuted
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiSurface
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val MEET_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
private val MEET_ZONE: ZoneId = ZoneId.systemDefault()

private fun formatCountdown(hours: Long, minutes: Long, secondsUntil: Long): String = when {
    secondsUntil <= 0 -> "Maintenant"
    hours > 0         -> "Dans ${hours}h ${minutes}min"
    minutes > 0       -> "Dans ${minutes}min"
    else              -> "< 1min"
}

@Composable
fun MeetTab(state: TabState<List<CalendarEvent>>, onRetry: () -> Unit) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            now = Instant.now()
        }
    }

    val meetEvents = remember(state, now) {
        if (state is TabState.Success) {
            state.data
                .filter { it.hangoutLink != null && it.end.isAfter(now) }
                .sortedBy { it.start }
        } else emptyList()
    }

    when (state) {
        is TabState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = AccentShu)
        }
        is TabState.Error -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message, color = SumiMuted, fontFamily = Mincho, fontSize = 15.sp)
                Spacer(Modifier.height(16.dp))
                SumiPrimaryButton("Réessayer", onRetry, Modifier.fillMaxWidth(0.5f))
            }
        }
        is TabState.Success -> {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { Spacer(Modifier.height(12.dp)) }

                // ── Saisie de code manuel ──────────────────────────────────
                item { JoinByCodeCard() }

                if (meetEvents.isEmpty()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    "Aucune réunion Meet à venir",
                                    color = SumiMuted,
                                    fontFamily = Mincho,
                                    fontSize = 16.sp,
                                )
                                Text(
                                    "Les réunions avec lien Meet apparaissent ici",
                                    color = SumiMuted.copy(alpha = 0.6f),
                                    fontFamily = Gothic,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                } else {
                    // Hero: first (soonest) meeting
                    item(key = meetEvents.first().id) {
                        HeroMeetCard(event = meetEvents.first(), now = now)
                    }
                    // Compact: remaining meetings
                    if (meetEvents.size > 1) {
                        item {
                            Text(
                                "Prochaines réunions",
                                color = SumiMuted,
                                fontFamily = Gothic,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                            )
                        }
                        items(meetEvents.drop(1), key = { it.id }) { event ->
                            CompactMeetCard(event = event, now = now)
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun JoinByCodeCard() {
    val ctx = LocalContext.current
    var code by remember { mutableStateOf("") }

    fun join() {
        val cleaned = code.trim().replace(" ", "").lowercase()
        if (cleaned.isBlank()) return
        val url = if (cleaned.startsWith("http")) cleaned
                  else "https://meet.google.com/$cleaned"
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        code = ""
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SumiSurface.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            placeholder = {
                Text("Code de réunion", color = SumiMuted, fontFamily = Mincho, fontSize = 14.sp)
            },
            singleLine = true,
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { join() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentShu,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                cursorColor = AccentShu,
            ),
        )
        Box(
            Modifier
                .wrapContentHeight()
                .clip(RoundedCornerShape(12.dp))
                .background(AccentShu)
                .clickable { join() }
                .padding(horizontal = 18.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Rejoindre", color = OnShu, fontFamily = Mincho, fontSize = 15.sp)
        }
    }
}

@Composable
private fun HeroMeetCard(event: CalendarEvent, now: Instant) {
    val ctx = LocalContext.current
    val isLive = event.start.isBefore(now)
    val secondsUntil = (event.start.epochSecond - now.epochSecond).coerceAtLeast(0)
    val hours = secondsUntil / 3600
    val minutes = (secondsUntil % 3600) / 60

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SumiSurface)
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left: meeting info (60%)
        Column(Modifier.weight(0.6f).padding(end = 24.dp)) {
            // Status badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (isLive) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(AccentShu))
                }
                Text(
                    text = if (isLive) "EN COURS" else formatCountdown(hours, minutes, secondsUntil),
                    color = AccentShu,
                    fontFamily = Gothic,
                    fontSize = 12.sp,
                    letterSpacing = 1.2.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = event.title,
                color = Kinari,
                fontFamily = Mincho,
                fontSize = 22.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            val startStr = event.start.atZone(MEET_ZONE).format(MEET_TIME_FMT)
            val endStr = event.end.atZone(MEET_ZONE).format(MEET_TIME_FMT)
            Text(
                text = "$startStr – $endStr",
                color = SumiMuted,
                fontFamily = Gothic,
                fontSize = 14.sp,
            )
        }

        // Right: big join button (40%)
        Column(
            Modifier.weight(0.4f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SumiPrimaryButton(
                text = if (isLive) "Rejoindre" else "Ouvrir le lien",
                onClick = {
                    event.hangoutLink?.let { link ->
                        ctx.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(link))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CompactMeetCard(event: CalendarEvent, now: Instant) {
    val ctx = LocalContext.current
    val secondsUntil = (event.start.epochSecond - now.epochSecond).coerceAtLeast(0)
    val hours = secondsUntil / 3600
    val minutes = (secondsUntil % 3600) / 60
    val startStr = event.start.atZone(MEET_ZONE).format(MEET_TIME_FMT)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SumiSurface.copy(alpha = 0.6f))
            .clickable {
                event.hangoutLink?.let { link ->
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(link))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                event.title,
                color = Kinari,
                fontFamily = Mincho,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(startStr, color = SumiMuted, fontFamily = Gothic, fontSize = 12.sp)
        }
        Text(
            text = formatCountdown(hours, minutes, secondsUntil),
            color = AccentShu,
            fontFamily = Gothic,
            fontSize = 11.sp,
        )
    }
}
