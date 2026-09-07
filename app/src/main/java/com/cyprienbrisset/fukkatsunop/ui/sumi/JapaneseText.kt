package com.cyprienbrisset.fukkatsunop.ui.sumi

import java.time.DayOfWeek

fun weekdayKanji(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> "月曜日"
    DayOfWeek.TUESDAY -> "火曜日"
    DayOfWeek.WEDNESDAY -> "水曜日"
    DayOfWeek.THURSDAY -> "木曜日"
    DayOfWeek.FRIDAY -> "金曜日"
    DayOfWeek.SATURDAY -> "土曜日"
    DayOfWeek.SUNDAY -> "日曜日"
}

fun stepHour(current: Int, delta: Int): Int = ((current + delta) % 24 + 24) % 24
fun stepMinute(current: Int, delta: Int): Int = ((current + delta) % 60 + 60) % 60
