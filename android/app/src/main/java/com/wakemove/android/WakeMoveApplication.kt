package com.wakemove.android

import android.app.AlarmManager
import android.app.Application
import android.util.Log
import androidx.room.Room
import com.wakemove.android.data.AlarmDatabase
import com.wakemove.android.data.RoomAlarmRepository
import com.wakemove.android.domain.AlarmRepository
import com.wakemove.android.health.AndroidHealthService
import com.wakemove.android.ringing.AndroidAlarmAudioPlayer
import com.wakemove.android.ringing.AndroidAlarmVibrator
import com.wakemove.android.ringing.RingingDependencies
import com.wakemove.android.ringing.RingingSessionController
import com.wakemove.android.scheduling.AlarmScheduler
import com.wakemove.android.scheduling.AndroidAlarmScheduler
import com.wakemove.android.scheduling.PendingScheduleRecovery
import com.wakemove.android.scheduling.SchedulingDependencies
import com.wakemove.android.ui.navigation.AlarmUiDependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WakeMoveApplication :
    Application(),
    SchedulingDependencies,
    RingingDependencies,
    AlarmUiDependencies {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override lateinit var alarmScheduler: AlarmScheduler
        private set
    override lateinit var alarmRepository: AlarmRepository
        private set
    override lateinit var healthService: AndroidHealthService
        private set
    override lateinit var pendingScheduleRecovery: PendingScheduleRecovery
        private set
    override lateinit var ringingSessionController: RingingSessionController
        private set

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(
            this,
            AlarmDatabase::class.java,
            DATABASE_NAME,
        ).build()
        val repository = RoomAlarmRepository(database.alarmDao())
        alarmRepository = repository
        healthService = AndroidHealthService(this)
        alarmScheduler = AndroidAlarmScheduler(
            context = this,
            alarmManager = getSystemService(AlarmManager::class.java),
            repository = repository,
        )
        pendingScheduleRecovery = PendingScheduleRecovery(repository, alarmScheduler)
        ringingSessionController = RingingSessionController(
            repository = repository,
            audioPlayer = AndroidAlarmAudioPlayer(this),
            vibrator = AndroidAlarmVibrator(this),
            scheduler = alarmScheduler,
            pendingScheduleRecovery = pendingScheduleRecovery,
        )
        applicationScope.launch {
            try {
                pendingScheduleRecovery.recover()
            } catch (error: Exception) {
                Log.e(TAG, "Unable to recover pending alarm schedules at startup", error)
            }
        }
    }

    companion object {
        private const val TAG = "WakeMoveApplication"
        private const val DATABASE_NAME = "wakemove.db"
    }
}
