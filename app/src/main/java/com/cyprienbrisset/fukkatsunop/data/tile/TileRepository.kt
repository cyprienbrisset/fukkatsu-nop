package com.cyprienbrisset.fukkatsunop.data.tile

import kotlinx.coroutines.flow.Flow

class TileRepository(private val dao: TileDao) {
    fun observeAll(): Flow<List<TileEntity>> = dao.observeAll()
    suspend fun getAll(): List<TileEntity> = dao.getAll()

    /** Adds a tile at the end, ignoring the passed-in position. */
    suspend fun add(tile: TileEntity): Long {
        val next = dao.maxPosition() + 1
        return dao.insert(tile.copy(position = next))
    }

    suspend fun update(tile: TileEntity) = dao.update(tile)
    suspend fun delete(tile: TileEntity) = dao.delete(tile)

    /** Rewrites positions to match the given order. */
    suspend fun reorder(ordered: List<TileEntity>) {
        dao.updateAll(ordered.mapIndexed { i, t -> t.copy(position = i) })
    }
}
