package com.cyprienbrisset.fukkatsunop.integration.google

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

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
        val now     = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val maxTime = now.plusSeconds(60L * 24 * 3600)
        val url = "$CALENDAR_URL?timeMin=$now&timeMax=$maxTime" +
                  "&maxResults=100&orderBy=startTime&singleEvents=true"
        val response = http.newCall(
            Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        ).execute()
        val raw = response.use {
            if (!it.isSuccessful) return@withContext emptyList()
            it.body?.string() ?: return@withContext emptyList()
        }
        parseEventsResponse(raw)
    }
    suspend fun createEvent(title: String, start: Instant, end: Instant): Boolean =
        withContext(Dispatchers.IO) {
            val token = authManager.validToken() ?: return@withContext false
            val s = start.truncatedTo(ChronoUnit.SECONDS)
            val e = end.truncatedTo(ChronoUnit.SECONDS)
            val json = """{"summary":"$title","start":{"dateTime":"$s"},"end":{"dateTime":"$e"}}"""
            val response = http.newCall(
                Request.Builder()
                    .url(CALENDAR_URL)
                    .header("Authorization", "Bearer $token")
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()
            response.use { it.isSuccessful }
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
            start = LocalDate.parse(
                startObj["date"]?.jsonPrimitive?.content ?: return@mapNotNull null
            ).atStartOfDay(ZoneOffset.UTC).toInstant()
            end = LocalDate.parse(
                endObj["date"]?.jsonPrimitive?.content ?: return@mapNotNull null
            ).atStartOfDay(ZoneOffset.UTC).toInstant()
        } else {
            start = OffsetDateTime.parse(
                startObj["dateTime"]?.jsonPrimitive?.content ?: return@mapNotNull null
            ).toInstant()
            end = OffsetDateTime.parse(
                endObj["dateTime"]?.jsonPrimitive?.content ?: return@mapNotNull null
            ).toInstant()
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
