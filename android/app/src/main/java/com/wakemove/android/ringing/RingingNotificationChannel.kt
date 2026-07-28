package com.wakemove.android.ringing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes

object RingingNotificationChannel {
    fun ensureCreated(context: Context) {
        val alarmAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannel(
            RingingService.NOTIFICATION_CHANNEL_ID,
            "Ringing alarms",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "WakeMove ringing alarm sessions"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
            setSound(null, alarmAttributes)
            enableVibration(false)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}
