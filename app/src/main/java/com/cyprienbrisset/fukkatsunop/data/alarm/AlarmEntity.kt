package com.cyprienbrisset.fukkatsunop.data.alarm

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * repeatDays is a bitmask: bit 0 = Monday ... bit 6 = Sunday.
 * 0 means a one-shot alarm (fires once, then disables itself).
 */
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val repeatDays: Int = 0,
    val label: String = "",
    val enabled: Boolean = true,
    val ringtoneUri: String? = null,   // null = default alarm sound
    val snoozeMinutes: Int = 10,
)
