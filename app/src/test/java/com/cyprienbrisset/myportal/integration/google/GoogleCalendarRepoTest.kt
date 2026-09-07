package com.cyprienbrisset.myportal.integration.google

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.ZoneOffset

class GoogleCalendarRepoTest {

    private val sampleJson = """
    {
      "items": [
        {
          "id": "evt1",
          "summary": "Réunion Hebdo",
          "start": { "dateTime": "2026-09-08T10:00:00+02:00" },
          "end":   { "dateTime": "2026-09-08T11:00:00+02:00" },
          "location": "Salle B",
          "hangoutLink": "https://meet.google.com/abc-defg-hij"
        },
        {
          "id": "evt2",
          "summary": "Journée sans titre",
          "start": { "date": "2026-09-09" },
          "end":   { "date": "2026-09-10" }
        }
      ]
    }
    """.trimIndent()

    @Test fun parseEventsResponse_parsesDateTimeEvent() {
        val events = parseEventsResponse(sampleJson)
        assertEquals(2, events.size)
        val e = events[0]
        assertEquals("evt1",            e.id)
        assertEquals("Réunion Hebdo",   e.title)
        assertEquals("Salle B",         e.location)
        assertNotNull(e.hangoutLink)
        assertEquals(false, e.isAllDay)
        // 10:00+02:00 = 08:00 UTC
        assertEquals(8, e.start.atZone(ZoneOffset.UTC).hour)
    }

    @Test fun parseEventsResponse_parsesAllDayEvent() {
        val events = parseEventsResponse(sampleJson)
        val e = events[1]
        assertEquals("evt2",   e.id)
        assertEquals(true,     e.isAllDay)
        assertNull(e.hangoutLink)
        assertNull(e.location)
    }

    @Test fun parseEventsResponse_returnsEmptyOnNoItems() {
        assertEquals(emptyList<CalendarEvent>(), parseEventsResponse("""{"kind":"calendar#events"}"""))
    }
}
