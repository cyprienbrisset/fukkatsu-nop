package com.cyprienbrisset.fukkatsunop.data.weather

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WeatherCacheDao {
    @Query("SELECT * FROM weather_cache WHERE cityKey = :key LIMIT 1")
    suspend fun get(key: String): WeatherCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: WeatherCacheEntity)
}
