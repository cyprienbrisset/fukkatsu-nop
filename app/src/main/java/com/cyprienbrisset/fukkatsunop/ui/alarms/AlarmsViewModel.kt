package com.cyprienbrisset.fukkatsunop.ui.alarms

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cyprienbrisset.fukkatsunop.alarm.AlarmScheduler
import com.cyprienbrisset.fukkatsunop.data.AppDatabase
import com.cyprienbrisset.fukkatsunop.data.alarm.AlarmEntity
import com.cyprienbrisset.fukkatsunop.data.alarm.AlarmRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AlarmRepository(AppDatabase.get(app).alarmDao())
    private val scheduler = AlarmScheduler(app)
    val alarms = repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(hour: Int, minute: Int, repeatDays: Int, label: String, ringtoneUri: String?, snoozeMinutes: Int, videoEnabled: Boolean = false, volumeProgressive: Boolean = true, volumeLevel: Int = 100, id: Long = 0) = viewModelScope.launch {
        val newId = repo.upsert(AlarmEntity(
            id = id, hour = hour, minute = minute, repeatDays = repeatDays, label = label,
            enabled = true, ringtoneUri = ringtoneUri, snoozeMinutes = snoozeMinutes,
            videoEnabled = videoEnabled, volumeProgressive = volumeProgressive, volumeLevel = volumeLevel,
        ))
        repo.byId(newId)?.let { scheduler.schedule(it) }
    }

    fun toggle(alarm: AlarmEntity, enabled: Boolean) = viewModelScope.launch {
        repo.setEnabled(alarm, enabled)
        val updated = alarm.copy(enabled = enabled)
        if (enabled) scheduler.schedule(updated) else scheduler.cancel(alarm.id)
    }

    fun delete(alarm: AlarmEntity) = viewModelScope.launch {
        scheduler.cancel(alarm.id); repo.delete(alarm)
    }

    suspend fun getById(id: Long): AlarmEntity? = repo.byId(id)
}
