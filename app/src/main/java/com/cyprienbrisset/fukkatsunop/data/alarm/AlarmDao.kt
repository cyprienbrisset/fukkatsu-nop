package com.cyprienbrisset.fukkatsunop.data.alarm

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE enabled = 1")
    suspend fun enabledAlarms(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun byId(id: Long): AlarmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alarm: AlarmEntity): Long

    @Delete suspend fun delete(alarm: AlarmEntity)
}
