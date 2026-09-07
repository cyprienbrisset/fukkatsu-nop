package com.cyprienbrisset.fukkatsunop.data.tile

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TileDao {
    @Query("SELECT * FROM tiles ORDER BY position ASC")
    fun observeAll(): Flow<List<TileEntity>>

    @Query("SELECT * FROM tiles ORDER BY position ASC")
    suspend fun getAll(): List<TileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tile: TileEntity): Long

    @Update
    suspend fun update(tile: TileEntity)

    @Update
    suspend fun updateAll(tiles: List<TileEntity>)

    @Delete
    suspend fun delete(tile: TileEntity)

    @Query("SELECT COALESCE(MAX(position), -1) FROM tiles")
    suspend fun maxPosition(): Int
}
