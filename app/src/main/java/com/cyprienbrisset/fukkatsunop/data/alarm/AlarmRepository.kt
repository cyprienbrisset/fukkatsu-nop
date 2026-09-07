package com.cyprienbrisset.fukkatsunop.data.alarm

import kotlinx.coroutines.flow.Flow

class AlarmRepository(private val dao: AlarmDao) {
    fun observeAll(): Flow<List<AlarmEntity>> = dao.observeAll()
    suspend fun enabled(): List<AlarmEntity> = dao.enabledAlarms()
    suspend fun byId(id: Long): AlarmEntity? = dao.byId(id)
    suspend fun upsert(alarm: AlarmEntity): Long = dao.upsert(alarm)
    suspend fun delete(alarm: AlarmEntity) = dao.delete(alarm)
    suspend fun setEnabled(alarm: AlarmEntity, enabled: Boolean) = dao.upsert(alarm.copy(enabled = enabled))
}
