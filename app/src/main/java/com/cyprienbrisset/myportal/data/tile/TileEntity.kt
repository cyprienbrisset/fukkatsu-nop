package com.cyprienbrisset.myportal.data.tile

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TileType { APP, WEB, AIRPLAY }

@Entity(tableName = "tiles")
data class TileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TileType,
    val label: String,
    val packageName: String? = null,
    val url: String? = null,
    val iconRef: String? = null,
    val position: Int,
)
