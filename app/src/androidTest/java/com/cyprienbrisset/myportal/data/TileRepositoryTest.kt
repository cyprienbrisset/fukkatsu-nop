package com.cyprienbrisset.fukkatsunop.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cyprienbrisset.fukkatsunop.data.tile.TileEntity
import com.cyprienbrisset.fukkatsunop.data.tile.TileRepository
import com.cyprienbrisset.fukkatsunop.data.tile.TileType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TileRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TileRepository

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        repo = TileRepository(db.tileDao())
    }

    @After fun teardown() = db.close()

    @Test fun addAppendsAtEndAndReorderPersists() = runTest {
        repo.add(TileEntity(type = TileType.APP, label = "Netflix", packageName = "com.netflix", position = 0))
        repo.add(TileEntity(type = TileType.WEB, label = "Jellyfin", url = "http://jelly", position = 0))
        val tiles = repo.getAll()
        assertEquals(listOf("Netflix", "Jellyfin"), tiles.map { it.label })
        assertEquals(listOf(0, 1), tiles.map { it.position })

        repo.reorder(tiles.reversed())
        assertEquals(listOf("Jellyfin", "Netflix"), repo.getAll().map { it.label })
    }
}
