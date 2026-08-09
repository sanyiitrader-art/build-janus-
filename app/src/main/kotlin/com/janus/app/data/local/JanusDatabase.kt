package com.janus.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Project Janus's Room database — currently a single table (devices).
 *
 * version = 1 since this is the first schema. Any future column
 * add/remove/rename must bump this version and either provide a Migration
 * or (during early development only) fall back to destructive migration —
 * this class intentionally does NOT enable fallbackToDestructiveMigration
 * by default, since silently wiping known devices on a schema change would
 * be a poor experience even pre-release; add real Migrations when the
 * schema changes.
 *
 * Excluded from backup/data-extraction — see backup_rules.xml and
 * data_extraction_rules.xml (spec #50: no unnecessary persistence of
 * device-identity data leaving the device via cloud backup).
 */
@Database(
    entities = [DeviceEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class JanusDatabase : RoomDatabase() {

    abstract fun deviceDao(): DeviceDao

    companion object {
        private const val DATABASE_NAME = "janus_database"

        @Volatile
        private var instance: JanusDatabase? = null

        fun getInstance(context: Context): JanusDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    JanusDatabase::class.java,
                    DATABASE_NAME
                ).build().also { instance = it }
            }
        }
    }
}