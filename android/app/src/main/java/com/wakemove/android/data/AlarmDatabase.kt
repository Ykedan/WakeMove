package com.wakemove.android.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AlarmEntity::class,
        RingingSessionEntity::class,
        AlarmEventEntity::class,
        AppSettingEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE alarms ADD COLUMN vibration_pattern " +
                        "TEXT NOT NULL DEFAULT 'GENTLE'",
                )
                db.execSQL(
                    "ALTER TABLE alarms ADD COLUMN vibration_intensity " +
                        "TEXT NOT NULL DEFAULT 'MEDIUM'",
                )
            }
        }
    }
}
