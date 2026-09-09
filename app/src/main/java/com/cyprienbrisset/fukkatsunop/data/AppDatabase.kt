package com.cyprienbrisset.fukkatsunop.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cyprienbrisset.fukkatsunop.data.alarm.AlarmDao
import com.cyprienbrisset.fukkatsunop.data.alarm.AlarmEntity
import com.cyprienbrisset.fukkatsunop.data.google.CalendarCacheDao
import com.cyprienbrisset.fukkatsunop.data.google.CalendarCacheEntity
import com.cyprienbrisset.fukkatsunop.data.tile.TileDao
import com.cyprienbrisset.fukkatsunop.data.tile.TileEntity
import com.cyprienbrisset.fukkatsunop.data.tile.TileType
import com.cyprienbrisset.fukkatsunop.data.weather.WeatherCacheDao
import com.cyprienbrisset.fukkatsunop.data.weather.WeatherCacheEntity
import timber.log.Timber

class Converters {
    @TypeConverter fun tileType(v: String): TileType = TileType.valueOf(v)
    @TypeConverter fun tileTypeToString(v: TileType): String = v.name
}

@Database(
    entities = [TileEntity::class, AlarmEntity::class, WeatherCacheEntity::class, CalendarCacheEntity::class],
    version = 7,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tileDao(): TileDao
    abstract fun alarmDao(): AlarmDao
    abstract fun weatherCacheDao(): WeatherCacheDao
    abstract fun calendarCacheDao(): CalendarCacheDao

    companion object {
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN volumeLevel INTEGER NOT NULL DEFAULT 100")
            }
        }

        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "myportal.db"
                ).addMigrations(MIGRATION_6_7).fallbackToDestructiveMigration().addCallback(object : RoomDatabase.Callback() {
                    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                        Timber.w("AppDatabase: migration destructive — toutes les données utilisateur ont été effacées (schema v${db.version})")
                    }
                }).build().also { instance = it }
            }
    }
}
