package com.cyprienbrisset.fukkatsunop.ui.google

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.fukkatsunop.integration.google.CalendarEvent
import com.cyprienbrisset.fukkatsunop.ui.sumi.SectionLabel
import com.cyprienbrisset.fukkatsunop.ui.sumi.SumiPrimaryButton
import com.cyprienbrisset.fukkatsunop.ui.theme.AccentShu
import com.cyprienbrisset.fukkatsunop.ui.theme.Gothic
import com.cyprienbrisset.fukkatsunop.ui.theme.Kinari
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiLine
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiMuted
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiSurface
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// ── Top-level formatters ──────────────────────────────────────────────────────

private val DATE_FMT = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.FRENCH)
private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

// ── View mode ────────────────────────────────────────────────────────────────

private enum class CalViewMode { MONTH, WEEK, LIST }

// ── View mode chips ───────────────────────────────────────────────────────────

@Composable
private fun ViewChips(mode: CalViewMode, onMode: (CalViewMode) -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(CalViewMode.MONTH to "Mois", CalViewMode.WEEK to "Semaine", CalViewMode.LIST to "Liste")
            .forEach { (m, label) ->
                val selected = mode == m
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (selected) AccentShu.copy(alpha = 0.18f) else SumiSurface)
                        .border(1.dp, if (selected) AccentShu.copy(alpha = 0.5f) else SumiLine, RoundedCornerShape(4.dp))
                        .clickable { onMode(m) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontFamily = Mincho, fontSize = 12.sp, color = if (selected) Kinari else SumiMuted)
                }
            }
    }
}

// ── Month view ────────────────────────────────────────────────────────────────

@Composable
private fun MonthView(
    events: List<CalendarEvent>,
    displayMonth: YearMonth,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)

    val eventsByDate: Map<LocalDate, List<CalendarEvent>> = remember(events, zone) {
        events.groupBy { it.start.atZone(zone).toLocalDate() }
    }

    Row(Modifier.fillMaxSize()) {
        // Left: month grid (42%)
        Column(Modifier.fillMaxWidth(0.42f).fillMaxHeight().padding(16.dp)) {
            val dayHeaders = listOf("L", "M", "M", "J", "V", "S", "D")
            Row(Modifier.fillMaxWidth()) {
                dayHeaders.forEach { d ->
                    Text(
                        d, Modifier.weight(1f), textAlign = TextAlign.Center,
                        color = SumiMuted, fontFamily = Gothic, fontSize = 11.sp,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            val firstDay = displayMonth.atDay(1)
            val startOffset = (firstDay.dayOfWeek.value - 1)
            val daysInMonth = displayMonth.lengthOfMonth()
            val totalCells = startOffset + daysInMonth
            val rows = (totalCells + 6) / 7

            repeat(rows) { row ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { col ->
                        val cellIndex = row * 7 + col
                        val dayNum = cellIndex - startOffset + 1
                        val date = if (dayNum in 1..daysInMonth) displayMonth.atDay(dayNum) else null
                        val hasEvents = date != null && eventsByDate.containsKey(date)
                        val isToday = date == today
                        val isSelected = date == selectedDate

                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when {
                                        isSelected -> AccentShu.copy(alpha = 0.25f)
                                        isToday    -> SumiSurface
                                        else       -> Color.Transparent
                                    }
                                )
                                .border(
                                    width = if (isToday) 1.dp else 0.dp,
                                    color = if (isToday) AccentShu.copy(alpha = 0.6f) else Color.Transparent,
                                    shape = RoundedCornerShape(6.dp),
                                )
                                .then(if (date != null) Modifier.clickable { onDateSelected(date) } else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (date != null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = dayNum.toString(),
                                        fontFamily = Mincho,
                                        fontSize = 13.sp,
                                        color = when {
                                            isSelected -> Kinari
                                            isToday    -> AccentShu
                                            date.month != displayMonth.month -> SumiMuted.copy(alpha = 0.4f)
                                            else       -> Kinari.copy(alpha = 0.85f)
                                        },
                                    )
                                    if (hasEvents) {
                                        Box(
                                            Modifier.size(4.dp).clip(CircleShape).background(AccentShu.copy(alpha = 0.8f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Vertical divider
        Box(Modifier.width(1.dp).fillMaxHeight().background(SumiLine))

        // Right: events for selected day (58%)
        val dayEvents = remember(selectedDate, events, zone) {
            events.filter { it.start.atZone(zone).toLocalDate() == selectedDate }
                  .sortedBy { it.start }
        }
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            val dayFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)
            Text(
                text = selectedDate.format(dayFmt).replaceFirstChar { it.uppercase() },
                fontFamily = Mincho, fontSize = 16.sp, color = Kinari,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            if (dayEvents.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Aucun événement", color = SumiMuted, fontFamily = Mincho, fontSize = 14.sp)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(dayEvents, key = { it.id }) { event ->
                        EventCard(event = event, zone = zone, now = remember { Instant.now() })
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

// ── Week view ─────────────────────────────────────────────────────────────────

@Composable
private fun WeekView(events: List<CalendarEvent>, selectedDate: LocalDate) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val monday = selectedDate.with(java.time.DayOfWeek.MONDAY)
    val weekDays = (0..6).map { monday.plusDays(it.toLong()) }
    val eventsByDate = remember(events, zone) {
        events.groupBy { it.start.atZone(zone).toLocalDate() }
    }
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }

    Row(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        weekDays.forEach { date ->
            val dayEvents = eventsByDate[date] ?: emptyList()
            val isToday = date == today
            val dayLabel = date.format(DateTimeFormatter.ofPattern("EEE d", Locale.FRENCH))
                               .replaceFirstChar { it.uppercase() }

            Column(
                Modifier.weight(1f).fillMaxHeight().padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isToday) AccentShu.copy(alpha = 0.18f) else SumiSurface)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        dayLabel,
                        fontFamily = Gothic,
                        fontSize = 11.sp,
                        color = if (isToday) AccentShu else SumiMuted,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(6.dp))

                LazyColumn(Modifier.fillMaxSize()) {
                    if (dayEvents.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().height(40.dp), Alignment.Center) {
                                Box(Modifier.width(1.dp).height(40.dp).background(SumiLine))
                            }
                        }
                    }
                    items(dayEvents, key = { it.id }) { event ->
                        val timeStr = if (event.isAllDay) "Journée"
                            else event.start.atZone(zone).format(timeFmt)
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(SumiSurface)
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                        ) {
                            Text(timeStr, fontFamily = Gothic, fontSize = 10.sp, color = AccentShu)
                            Text(
                                event.title,
                                fontFamily = Mincho, fontSize = 11.sp, color = Kinari,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

// ── List view ─────────────────────────────────────────────────────────────────

@Composable
private fun ListView(events: List<CalendarEvent>, zone: ZoneId) {
    if (events.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("Aucun événement à venir", color = SumiMuted, fontFamily = Mincho)
        }
        return
    }
    val today    = LocalDate.now(zone)
    val tomorrow = today.plusDays(1)
    val grouped  = events.groupBy { it.start.atZone(zone).toLocalDate() }
    val now = remember { Instant.now() }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        grouped.forEach { (date, dayEvents) ->
            val label = when (date) {
                today    -> "Aujourd'hui"
                tomorrow -> "Demain"
                else     -> date.format(DATE_FMT)
            }
            item(key = "header-$date") {
                Spacer(Modifier.height(20.dp))
                SectionLabel(kana = "", text = label.uppercase(Locale.FRENCH))
                Spacer(Modifier.height(8.dp))
            }
            items(dayEvents, key = { it.id }) { event ->
                EventCard(event = event, zone = zone, now = now)
                Spacer(Modifier.height(8.dp))
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ── AgendaTab ─────────────────────────────────────────────────────────────────

@Composable
fun AgendaTab(state: TabState<List<CalendarEvent>>, onRetry: () -> Unit) {
    var mode by remember { mutableStateOf(CalViewMode.MONTH) }
    var displayMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val zone = ZoneId.systemDefault()
    val monthFmt = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH) }

    when (state) {
        is TabState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AccentShu)
        }
        is TabState.Error -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message, color = SumiMuted, fontFamily = Mincho, fontSize = 15.sp)
                Spacer(Modifier.height(16.dp))
                SumiPrimaryButton("Réessayer", onRetry, Modifier.fillMaxWidth(0.5f))
            }
        }
        is TabState.Success -> {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(SumiSurface)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (mode != CalViewMode.LIST) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                displayMonth = displayMonth.minusMonths(1)
                                selectedDate = displayMonth.atDay(1)
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = SumiMuted)
                            }
                            Text(
                                displayMonth.format(monthFmt).replaceFirstChar { it.uppercase() },
                                fontFamily = Mincho, fontSize = 15.sp, color = Kinari,
                                modifier = Modifier.width(180.dp),
                                textAlign = TextAlign.Center,
                            )
                            IconButton(onClick = {
                                displayMonth = displayMonth.plusMonths(1)
                                selectedDate = displayMonth.atDay(1)
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = SumiMuted)
                            }
                        }
                    } else {
                        Spacer(Modifier.width(220.dp))
                    }

                    ViewChips(mode) { mode = it }
                }

                Box(Modifier.fillMaxSize()) {
                    when (mode) {
                        CalViewMode.MONTH -> MonthView(
                            events = state.data,
                            displayMonth = displayMonth,
                            selectedDate = selectedDate,
                            onDateSelected = { selectedDate = it },
                        )
                        CalViewMode.WEEK -> WeekView(
                            events = state.data,
                            selectedDate = selectedDate,
                        )
                        CalViewMode.LIST -> ListView(events = state.data, zone = zone)
                    }
                }
            }
        }
    }
}

// ── EventCard (shared) ────────────────────────────────────────────────────────

@Composable
private fun EventCard(event: CalendarEvent, zone: ZoneId, now: Instant) {
    val ctx = LocalContext.current
    val isImminent = event.start.isAfter(now) && event.start.isBefore(now.plusSeconds(3600))
    val accentColor = if (isImminent) AccentShu else SumiSurface

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
