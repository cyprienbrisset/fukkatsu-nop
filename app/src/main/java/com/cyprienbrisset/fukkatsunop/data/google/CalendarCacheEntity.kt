package com.cyprienbrisset.fukkatsunop.data.google

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendar_cache")
data class CalendarCacheEntity(
    @PrimaryKey val id: Int = 1,
    val eventsJson: String,
    val fetchedAt: Long,
)
