package com.wakemove.android.scheduling

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import com.wakemove.android.MainActivity
import com.wakemove.android.ringing.RingingService

class AlarmFallbackNotifier(private val context: Context) {
    fun show(alarmId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("POST_NOTIFICATIONS permission is unavailable")
        }
        val openApp = PendingIntent.getActivity(
            context,
            20_003,
            Intent(context, MainActivity::class.java)
                .setAction(RingingService.ACTION_SHOW_RINGING)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(
            context,
            RingingService.NOTIFICATION_CHANNEL_ID,
        )
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("WakeMove 闹钟启动失败")
            .setContentText("点此打开 WakeMove，并检查系统后台运行设置")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(openApp)
            .setFullScreenIntent(openApp, true)
            .setAutoCancel(false)
            .build()
        NotificationManagerCompat.from(context).notify(10_002, notification)
    }
}
