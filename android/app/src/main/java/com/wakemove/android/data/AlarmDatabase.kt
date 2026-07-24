package com.wakemove.android.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        AlarmEntity::class,
        RingingSessionEntity::class,
        AlarmEventEntity::class,
        AppSettingEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
}
