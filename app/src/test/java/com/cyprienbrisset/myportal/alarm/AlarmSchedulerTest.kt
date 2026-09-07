package com.cyprienbrisset.fukkatsunop.alarm

import com.cyprienbrisset.fukkatsunop.data.alarm.AlarmEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class AlarmSchedulerTest {
    // Reference "now": Wednesday 2026-09-02, 10:00.
    private val now = LocalDateTime.of(2026, 9, 2, 10, 0)

    @Test fun oneShotLaterToday() {
        val a = AlarmEntity(hour = 14, minute = 30, repeatDays = 0)
        assertEquals(LocalDateTime.of(2026, 9, 2, 14, 30), nextTriggerTime(a, now))
    }

    @Test fun oneShotAlreadyPassedRollsToTomorrow() {
        val a = AlarmEntity(hour = 8, minute = 0, repeatDays = 0)
        assertEquals(LocalDateTime.of(2026, 9, 3, 8, 0), nextTriggerTime(a, now))
    }

    @Test fun repeatingPicksNextMatchingDay() {
        val a = AlarmEntity(hour = 7, minute = 0, repeatDays = 1 shl 0) // Mondays
        assertEquals(LocalDateTime.of(2026, 9, 7, 7, 0), nextTriggerTime(a, now))
    }

    @Test fun repeatingTodayButTimePassedGoesNextWeek() {
        val a = AlarmEntity(hour = 8, minute = 0, repeatDays = 1 shl 2) // Wednesdays, 08:00 passed
        assertEquals(LocalDateTime.of(2026, 9, 9, 8, 0), nextTriggerTime(a, now))
    }

    @Test fun repeatingTodayTimeNotPassedFiresToday() {
        val a = AlarmEntity(hour = 22, minute = 0, repeatDays = 1 shl 2) // Wednesday 22:00
        assertEquals(LocalDateTime.of(2026, 9, 2, 22, 0), nextTriggerTime(a, now))
    }
}
