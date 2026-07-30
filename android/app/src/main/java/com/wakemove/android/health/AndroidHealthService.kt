package com.wakemove.android.health

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.wakemove.android.ringing.RingingService

enum class HealthStatus {
    READY,
    ACTION_REQUIRED,
    UNAVAILABLE,
}

data class HealthSnapshot(
    val exactAlarm: HealthStatus,
    val notifications: HealthStatus,
    val fullScreenIntent: HealthStatus,
    val camera: HealthStatus,
    val microphone: HealthStatus,
    val notificationChannel: HealthStatus = HealthStatus.READY,
    val speechRecognition: HealthStatus = HealthStatus.READY,
) {
    val canScheduleAlarms: Boolean
        get() = exactAlarm == HealthStatus.READY &&
            notifications == HealthStatus.READY &&
            notificationChannel == HealthStatus.READY &&
            fullScreenIntent == HealthStatus.READY
}

class AndroidHealthService(
    context: Context,
    private val alarmManager: AlarmManager? =
        context.getSystemService(AlarmManager::class.java),
    private val notificationManager: NotificationManager? =
        context.getSystemService(NotificationManager::class.java),
    private val fullScreenIntentAllowed: () -> Boolean = {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            notificationManager?.canUseFullScreenIntent() == true
    },
) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    fun snapshot(): HealthSnapshot = HealthSnapshot(
        exactAlarm = exactAlarmStatus(),
        notifications = notificationStatus(),
        fullScreenIntent = fullScreenIntentStatus(),
        camera = sensorStatus(
            feature = PackageManager.FEATURE_CAMERA_ANY,
            permission = Manifest.permission.CAMERA,
        ),
        microphone = sensorStatus(
            feature = PackageManager.FEATURE_MICROPHONE,
            permission = Manifest.permission.RECORD_AUDIO,
        ),
        notificationChannel = notificationChannelStatus(),
        speechRecognition = HealthStatus.READY,
    )

    private fun exactAlarmStatus(): HealthStatus {
        val manager = alarmManager ?: return HealthStatus.UNAVAILABLE
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            manager.canScheduleExactAlarms()
        ) {
            HealthStatus.READY
        } else {
            HealthStatus.ACTION_REQUIRED
        }
    }

    private fun notificationStatus(): HealthStatus {
        val manager = notificationManager ?: return HealthStatus.UNAVAILABLE
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        return if (permissionGranted && manager.areNotificationsEnabled()) {
            HealthStatus.READY
        } else {
            HealthStatus.ACTION_REQUIRED
        }
    }

    private fun notificationChannelStatus(): HealthStatus {
        val manager = notificationManager ?: return HealthStatus.UNAVAILABLE
        val channel = manager.getNotificationChannel(RingingService.NOTIFICATION_CHANNEL_ID)
            ?: return HealthStatus.ACTION_REQUIRED
        return if (channel.importance < NotificationManager.IMPORTANCE_HIGH) {
            HealthStatus.ACTION_REQUIRED
        } else {
            HealthStatus.READY
        }
    }

    private fun fullScreenIntentStatus(): HealthStatus {
        if (notificationManager == null) return HealthStatus.UNAVAILABLE
        if (!hasPermission(Manifest.permission.USE_FULL_SCREEN_INTENT)) {
            return HealthStatus.UNAVAILABLE
        }
        return if (fullScreenIntentAllowed()) {
            HealthStatus.READY
        } else {
            HealthStatus.ACTION_REQUIRED
        }
    }

    private fun sensorStatus(feature: String, permission: String): HealthStatus {
        if (!packageManager.hasSystemFeature(feature)) return HealthStatus.UNAVAILABLE
        return if (hasPermission(permission)) {
            HealthStatus.READY
        } else {
            HealthStatus.ACTION_REQUIRED
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) ==
            PackageManager.PERMISSION_GRANTED
}
