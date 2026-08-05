package com.wakemove.android

import android.app.AlarmManager
import android.app.Application
import android.util.Log
import androidx.room.Room
import androidx.work.Configuration
import com.wakemove.android.data.AlarmDatabase
import com.wakemove.android.data.RoomAlarmRepository
import com.wakemove.android.domain.AlarmRepository
import com.wakemove.android.health.AndroidHealthService
import com.wakemove.android.i18n.WakeMoveLocale
import com.wakemove.android.ringing.AndroidAlarmAudioPlayer
import com.wakemove.android.ringing.AndroidAlarmVibrator
import com.wakemove.android.ringing.RingingDependencies
import com.wakemove.android.ringing.RingingDeliveryDependencies
import com.wakemove.android.ringing.RingingNotificationChannel
import com.wakemove.android.ringing.RingingSessionController
import com.wakemove.android.scheduling.AlarmScheduler
import com.wakemove.android.scheduling.AlarmDeliveryCoordinator
import com.wakemove.android.scheduling.AlarmDeliveryDiagnostics
import com.wakemove.android.scheduling.AndroidAlarmScheduler
import com.wakemove.android.scheduling.PendingScheduleRecovery
import com.wakemove.android.scheduling.SchedulingDependencies
import com.wakemove.android.scheduling.StartupAlarmRecovery
import com.wakemove.android.ui.navigation.AlarmUiDependencies
import com.wakemove.android.update.AppUpdateManager
import com.wakemove.android.update.AppUpdateNotifications
import com.wakemove.android.update.AppUpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WakeMoveApplication :
    Application(),
    SchedulingDependencies,
    RingingDependencies,
    RingingDeliveryDependencies,
    AlarmUiDependencies,
    Configuration.Provider {
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
    override lateinit var alarmDeliveryDiagnostics: AlarmDeliveryDiagnostics
        private set
    override lateinit var alarmDeliveryCoordinator: AlarmDeliveryCoordinator
        private set
    override lateinit var appUpdateManager: AppUpdateManager
        private set
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        WakeMoveLocale.initialize(this)
        RingingNotificationChannel.ensureCreated(this)
        AppUpdateNotifications.ensureChannel(this)
        appUpdateManager = AppUpdateManager(this, applicationScope)
        AppUpdateWorker.schedule(this)
        val database = Room.databaseBuilder(
            this,
            AlarmDatabase::class.java,
            DATABASE_NAME,
        )
            .addMigrations(AlarmDatabase.MIGRATION_1_2)
            .build()
        val repository = RoomAlarmRepository(database.alarmDao())
        alarmRepository = repository
        healthService = AndroidHealthService(this)
        alarmDeliveryDiagnostics = AlarmDeliveryDiagnostics(this)
        alarmScheduler = AndroidAlarmScheduler(
            context = this,
            alarmManager = getSystemService(AlarmManager::class.java),
            repository = repository,
            deliveryDiagnostics = alarmDeliveryDiagnostics,
        )
        alarmDeliveryCoordinator = AlarmDeliveryCoordinator(
            context = this,
            scheduler = alarmScheduler,
            diagnostics = alarmDeliveryDiagnostics,
        )
        pendingScheduleRecovery = PendingScheduleRecovery(repository, alarmScheduler)
        ringingSessionController = RingingSessionController(
            repository = repository,
            audioPlayer = AndroidAlarmAudioPlayer(this),
            vibrator = AndroidAlarmVibrator(this),
            scheduler = alarmScheduler,
            pendingScheduleRecovery = pendingScheduleRecovery,
            deliveryDiagnostics = alarmDeliveryDiagnostics,
        )
        val startupAlarmRecovery = StartupAlarmRecovery(
            repository = repository,
            scheduler = alarmScheduler,
            pendingScheduleRecovery = pendingScheduleRecovery,
            deliveryCoordinator = alarmDeliveryCoordinator,
            diagnostics = alarmDeliveryDiagnostics,
        )
        applicationScope.launch {
            try {
                startupAlarmRecovery.recover()
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
