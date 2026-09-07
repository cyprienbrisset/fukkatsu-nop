package com.cyprienbrisset.fukkatsunop.ui.sumi

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

class JapaneseTextTest {
    @Test fun weekdayKanjiMapsAllDays() {
        assertEquals("月曜日", weekdayKanji(DayOfWeek.MONDAY))
        assertEquals("水曜日", weekdayKanji(DayOfWeek.WEDNESDAY))
        assertEquals("日曜日", weekdayKanji(DayOfWeek.SUNDAY))
    }
    @Test fun stepHourWraps() {
        assertEquals(0, stepHour(23, +1))
        assertEquals(23, stepHour(0, -1))
        assertEquals(8, stepHour(7, +1))
    }
    @Test fun stepMinuteWraps() {
        assertEquals(0, stepMinute(59, +1))
        assertEquals(59, stepMinute(0, -1))
        assertEquals(31, stepMinute(30, +1))
    }
}
