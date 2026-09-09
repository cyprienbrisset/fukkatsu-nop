package com.cyprienbrisset.fukkatsunop.data.google

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CalendarCacheDao {
    @Query("SELECT * FROM calendar_cache WHERE id = 1 LIMIT 1")
    suspend fun get(): CalendarCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: CalendarCacheEntity)
}
