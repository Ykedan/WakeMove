package com.wakemove.android.ringing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

interface RingingDependencies {
    val ringingSessionController: RingingSessionController
}

class RingingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val binder = RingingBinder()
    private lateinit var controller: RingingSessionController
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        val dependencies = applicationContext as? RingingDependencies
            ?: error("Application must implement RingingDependencies")
        controller = dependencies.ringingSessionController
        createNotificationChannel()
        serviceScope.launch {
            controller.state.collectLatest { state ->
                val status = state.session?.status ?: return@collectLatest
                if (status != com.wakemove.android.domain.SessionStatus.RINGING) {
                    stopForegroundSession()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getStringExtra(AlarmReceiver.EXTRA_ALARM_ID)
        startForegroundImmediately(alarmId)
        acquireWakeLock()

        serviceScope.launch {
            when (intent?.action) {
                AlarmReceiver.ACTION_START_RINGING ->
                    alarmId?.let { controller.start(it) }

                ACTION_SNOOZE -> controller.snooze()
                ACTION_COMPLETE -> controller.complete()
                ACTION_BYPASS -> controller.bypass()
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        serviceScope.cancel()
        controller.releaseAlerting()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun startForegroundImmediately(alarmId: String?) {
        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("WakeMove alarm")
            .setContentText("Complete your wake-up challenge")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenIntent(alarmId))
            .setFullScreenIntent(fullScreenIntent(alarmId), true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "Snooze",
                    servicePendingIntent(ACTION_SNOOZE, REQUEST_SNOOZE),
                ).build(),
            )
            .build()
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private fun createNotificationChannel() {
        val alarmAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Ringing alarms",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "WakeMove ringing alarm sessions"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
            setSound(null, alarmAttributes)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
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

    private fun stopForegroundSession() {
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf(PowerManager.WakeLock::isHeld)?.release()
        wakeLock = null
    }

    private fun fullScreenIntent(alarmId: String?): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(ACTION_SHOW_RINGING)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
        return PendingIntent.getActivity(
            this,
            REQUEST_FULL_SCREEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, RingingService::class.java).setAction(action),
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

        private const val NOTIFICATION_ID = 10_001
        private const val REQUEST_FULL_SCREEN = 20_001
        private const val REQUEST_SNOOZE = 20_002
        private const val WAKE_LOCK_TAG = "WakeMove:Ringing"
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 15 * 60 * 1_000L
    }
}

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
