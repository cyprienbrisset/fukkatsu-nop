package com.cyprienbrisset.fukkatsunop.system

import java.time.Duration
import java.time.LocalDateTime

/** Millis from [now] until the next occurrence of [hour]:00 (today if still ahead, else tomorrow). */
fun millisUntilHour(now: LocalDateTime, hour: Int): Long {
    var target = now.toLocalDate().atTime(hour, 0)
    if (!target.isAfter(now)) target = target.plusDays(1)
    return Duration.between(now, target).toMillis()
}
