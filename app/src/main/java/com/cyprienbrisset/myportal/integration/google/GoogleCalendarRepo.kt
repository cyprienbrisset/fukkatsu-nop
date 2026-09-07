package com.cyprienbrisset.myportal.integration.google

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class CalendarEvent(
    val id: String,
    val title: String,
    val start: Instant,
    val end: Instant,
    val location: String?,
    val hangoutLink: String?,
    val isAllDay: Boolean,
)

private const val CALENDAR_URL =
    "https://www.googleapis.com/calendar/v3/calendars/primary/events"

class GoogleCalendarRepo(
    private val authManager: GoogleAuthManager,
    private val http: OkHttpClient = OkHttpClient(),
) {
    suspend fun fetchEvents(): List<CalendarEvent> = withContext(Dispatchers.IO) {
        val token = authManager.validToken() ?: return@withContext emptyList()
        val now     = Instant.now()
        val maxTime = now.plusSeconds(14L * 24 * 3600)
        val url = "$CALENDAR_URL?timeMin=${now}&timeMax=${maxTime}" +
                  "&maxResults=50&orderBy=startTime&singleEvents=true"
        val raw = http.newCall(
            Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        ).execute().use { it.body!!.string() }
        parseEventsResponse(raw)
    }
}

// ── Pure helper ──────────────────────────────────────────────────────────────

internal fun parseEventsResponse(raw: String): List<CalendarEvent> {
    val items = Json.parseToJsonElement(raw).jsonObject["items"]?.jsonArray
        ?: return emptyList()
    return items.mapNotNull { item ->
        val obj     = item.jsonObject
        val id      = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val title   = obj["summary"]?.jsonPrimitive?.content ?: "(sans titre)"
        val startObj = obj["start"]?.jsonObject ?: return@mapNotNull null
        val endObj   = obj["end"]?.jsonObject   ?: return@mapNotNull null
        val isAllDay = startObj["date"] != null
        val start: Instant
        val end: Instant
        if (isAllDay) {
            start = LocalDate.parse(startObj["date"]!!.jsonPrimitive.content)
                .atStartOfDay(ZoneOffset.UTC).toInstant()
            end   = LocalDate.parse(endObj["date"]!!.jsonPrimitive.content)
                .atStartOfDay(ZoneOffset.UTC).toInstant()
        } else {
            start = OffsetDateTime.parse(startObj["dateTime"]!!.jsonPrimitive.content).toInstant()
            end   = OffsetDateTime.parse(endObj["dateTime"]!!.jsonPrimitive.content).toInstant()
        }
        CalendarEvent(
            id          = id,
            title       = title,
            start       = start,
            end         = end,
            location    = obj["location"]?.jsonPrimitive?.content,
            hangoutLink = obj["hangoutLink"]?.jsonPrimitive?.content,
            isAllDay    = isAllDay,
        )
    }
}
