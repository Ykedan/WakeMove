package com.wakemove.android

import android.app.AlarmManager
import android.app.Application
import androidx.room.Room
import com.wakemove.android.data.AlarmDatabase
import com.wakemove.android.data.RoomAlarmRepository
import com.wakemove.android.scheduling.AlarmScheduler
import com.wakemove.android.scheduling.AndroidAlarmScheduler
import com.wakemove.android.scheduling.SchedulingDependencies

class WakeMoveApplication : Application(), SchedulingDependencies {
    override lateinit var alarmScheduler: AlarmScheduler
        private set

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(
            this,
            AlarmDatabase::class.java,
            DATABASE_NAME,
        ).build()
        val repository = RoomAlarmRepository(database.alarmDao())
        alarmScheduler = AndroidAlarmScheduler(
            context = this,
            alarmManager = getSystemService(AlarmManager::class.java),
            repository = repository,
        )
    }

    companion object {
        private const val DATABASE_NAME = "wakemove.db"
    }
}
