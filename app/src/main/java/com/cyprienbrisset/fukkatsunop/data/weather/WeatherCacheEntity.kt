package com.cyprienbrisset.fukkatsunop.data.weather

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weather_cache")
data class WeatherCacheEntity(
    @PrimaryKey val cityKey: String,
    val temperatureC: Int,
    val description: String,
    val fetchedAt: Long,
)
