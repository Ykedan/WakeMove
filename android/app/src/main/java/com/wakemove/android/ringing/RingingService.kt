package com.wakemove.android.ringing

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.VibrationAttributes
import android.os.Vibrator
import android.os.VibratorManager
import com.wakemove.android.MainActivity
import com.wakemove.android.scheduling.AlarmReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface RingingDependencies {
    val ringingSessionController: RingingSessionController
}

class RingingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val binder = RingingBinder()
    private val commandMutex = Mutex()
    private lateinit var controller: RingingSessionController
    private var wakeLock: PowerManager.WakeLock? = null
    private var ownership: SessionOwnership? = null
    private var terminalObservation: Job? = null

    override fun onCreate() {
        super.onCreate()
        val dependencies = applicationContext as? RingingDependencies
            ?: error("Application must implement RingingDependencies")
        controller = dependencies.ringingSessionController
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getStringExtra(AlarmReceiver.EXTRA_ALARM_ID)
        val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID)
        startForegroundImmediately(alarmId, sessionId)
        acquireWakeLock()

        serviceScope.launch {
            commandMutex.withLock {
                try {
                    when (intent?.action) {
                        AlarmReceiver.ACTION_START_RINGING ->
                            handleStart(alarmId, startId)

                        ACTION_SNOOZE, ACTION_COMPLETE, ACTION_BYPASS ->
                            handleSessionCommand(
                                action = checkNotNull(intent.action),
                                alarmId = alarmId,
                                sessionId = sessionId,
                                startId = startId,
                            )

                        else -> rejectCommand(startId)
                    }
                } catch (_: Exception) {
                    rejectCommand(startId)
                }
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        terminalObservation?.cancel()
        serviceScope.cancel()
        controller.releaseAlerting()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private suspend fun handleStart(alarmId: String?, startId: Int) {
        if (alarmId == null) {
            rejectCommand(startId)
            return
        }
        val started = controller.start(alarmId)
        val state = controller.state.value
        val session = state.session
        if ((!started && !state.isRinging(alarmId)) ||
            session == null ||
            session.status != com.wakemove.android.domain.SessionStatus.RINGING
        ) {
            rejectCommand(startId)
            return
        }

        claimSession(startId, alarmId, session.id)
        controller.recoverPendingSchedules()
    }

    private suspend fun handleSessionCommand(
        action: String,
        alarmId: String?,
        sessionId: String?,
        startId: Int,
    ) {
        if (alarmId == null || sessionId == null ||
            !hydrateAddressedSession(alarmId, sessionId)
        ) {
            rejectCommand(startId)
            return
        }

        claimSession(startId, alarmId, sessionId)
        val transitioned = when (action) {
            ACTION_SNOOZE -> controller.snooze()
            ACTION_COMPLETE -> controller.complete()
            ACTION_BYPASS -> controller.bypass()
            else -> false
        }
        if (transitioned) {
            stopOwnedSession(SessionOwnership(startId, alarmId, sessionId))
        } else {
            rejectCommand(startId)
        }
    }

    private suspend fun hydrateAddressedSession(
        alarmId: String,
        sessionId: String,
    ): Boolean {
        if (controller.state.value.isRinging(alarmId, sessionId)) return true
        controller.start(alarmId)
        return controller.state.value.isRinging(alarmId, sessionId)
    }

    private fun claimSession(startId: Int, alarmId: String, sessionId: String) {
        ownership = SessionOwnership(startId, alarmId, sessionId)
        terminalObservation?.cancel()
        startForegroundImmediately(alarmId, sessionId)
        terminalObservation = serviceScope.launch {
            controller.state.collect { state ->
                val owned = ownership ?: return@collect
                val session = state.session ?: return@collect
                if (session.id == owned.sessionId &&
                    session.alarmId == owned.alarmId &&
                    session.status != com.wakemove.android.domain.SessionStatus.RINGING
                ) {
                    stopOwnedSession(owned)
                }
            }
        }
    }

    private fun rejectCommand(startId: Int) {
        val owned = ownership
        if (owned != null &&
            controller.state.value.isRinging(owned.alarmId, owned.sessionId)
        ) {
            val promoted = owned.copy(startId = maxOf(owned.startId, startId))
            ownership = promoted
            startForegroundImmediately(promoted.alarmId, promoted.sessionId)
            return
        }
        if (!stopSelfResult(startId)) return
        ownership = null
        terminalObservation?.cancel()
        terminalObservation = null
        releaseForegroundResources()
    }

    private fun stopOwnedSession(expected: SessionOwnership) {
        if (ownership != expected) return
        if (!stopSelfResult(expected.startId)) return
        ownership = null
        terminalObservation?.cancel()
        terminalObservation = null
        releaseForegroundResources()
    }

    private fun releaseForegroundResources() {
        controller.releaseAlerting()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun startForegroundImmediately(alarmId: String?, sessionId: String?) {
        val builder = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("WakeMove alarm")
            .setContentText("Complete your wake-up challenge")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenIntent(alarmId, sessionId))
            .setFullScreenIntent(fullScreenIntent(alarmId, sessionId), true)
        if (alarmId != null && sessionId != null &&
            controller.state.value.canSnooze(alarmId, sessionId)
        ) {
            builder.addAction(
                Notification.Action.Builder(
                    null,
                    "Snooze",
                    servicePendingIntent(
                        action = ACTION_SNOOZE,
                        requestCode = REQUEST_SNOOZE,
                        alarmId = alarmId,
                        sessionId = sessionId,
                    ),
                ).build(),
            )
        }
        startForeground(
            NOTIFICATION_ID,
            builder.build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private fun createNotificationChannel() {
        RingingNotificationChannel.ensureCreated(this)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MILLIS)
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf(PowerManager.WakeLock::isHeld)?.release()
        wakeLock = null
    }

    private fun fullScreenIntent(alarmId: String?, sessionId: String?): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(ACTION_SHOW_RINGING)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_SESSION_ID, sessionId)
        return PendingIntent.getActivity(
            this,
            REQUEST_FULL_SCREEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun servicePendingIntent(
        action: String,
        requestCode: Int,
        alarmId: String,
        sessionId: String,
    ): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, RingingService::class.java)
                .setAction(action)
                .setData(
                    Uri.Builder()
                        .scheme("wakemove")
                        .authority("ringing")
                        .appendPath(sessionId)
                        .appendPath(action)
                        .build(),
                )
                .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
                .putExtra(EXTRA_SESSION_ID, sessionId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    inner class RingingBinder : Binder() {
        val sessionController: RingingSessionController
            get() = controller
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "ringing_alarms"
        const val ACTION_SHOW_RINGING = "com.wakemove.android.action.SHOW_RINGING"
        const val ACTION_SNOOZE = "com.wakemove.android.action.SNOOZE"
        const val ACTION_COMPLETE = "com.wakemove.android.action.COMPLETE"
        const val ACTION_BYPASS = "com.wakemove.android.action.BYPASS"
        const val EXTRA_SESSION_ID = "com.wakemove.android.extra.SESSION_ID"

        private const val NOTIFICATION_ID = 10_001
        private const val REQUEST_FULL_SCREEN = 20_001
        private const val REQUEST_SNOOZE = 20_002
        private const val WAKE_LOCK_TAG = "WakeMove:Ringing"
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 15 * 60 * 1_000L
    }

    private data class SessionOwnership(
        val startId: Int,
        val alarmId: String,
        val sessionId: String,
    )
}

private fun RingingUiState.isRinging(alarmId: String, sessionId: String? = null): Boolean =
    alarm?.id == alarmId &&
        session?.status == com.wakemove.android.domain.SessionStatus.RINGING &&
        (sessionId == null || session.id == sessionId)

private fun RingingUiState.canSnooze(alarmId: String, sessionId: String): Boolean =
    isRinging(alarmId, sessionId) && remainingSnoozes > 0

class AndroidAlarmVibrator(
    context: Context,
) : AlarmVibrator {
    private val vibrator: Vibrator =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    private val alarmAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    override fun start() {
        val effect = VibrationEffect.createWaveform(
            longArrayOf(0, VIBRATE_MILLIS, PAUSE_MILLIS),
            REPEAT_FROM_START,
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(
                effect,
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, alarmAttributes)
        }
    }

    override fun stop() {
        vibrator.cancel()
    }

    private companion object {
        const val VIBRATE_MILLIS = 500L
        const val PAUSE_MILLIS = 500L
        const val REPEAT_FROM_START = 0
    }
}
