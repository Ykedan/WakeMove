package com.wakemove.android.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wakemove.android.BuildConfig
import com.wakemove.android.MainActivity
import java.util.concurrent.TimeUnit

class AppUpdateWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val onboardingComplete = applicationContext.getSharedPreferences(
            MainActivity.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).getBoolean(MainActivity.KEY_ONBOARDING_COMPLETE, false)
        if (!onboardingComplete) return Result.success()

        return runCatching { GitHubUpdateRepository().latestRelease() }
            .fold(
                onSuccess = { info ->
                    val ignoredVersion = applicationContext.getSharedPreferences(
                        AppUpdateManager.PREFERENCES_NAME,
                        Context.MODE_PRIVATE,
                    ).getString(AppUpdateManager.KEY_IGNORED_VERSION, null)
                    if (info.versionCode > BuildConfig.VERSION_CODE &&
                        ignoredVersion != info.versionName
                    ) {
                        AppUpdateNotifications.showAvailable(applicationContext, info)
                    }
                    Result.success()
                },
                onFailure = {
                    if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.success()
                },
            )
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "wakemove_update_check"
        private const val MAX_RETRY_ATTEMPTS = 3

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val work = PeriodicWorkRequestBuilder<AppUpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                work,
            )
        }
    }
}

object AppUpdateNotifications {
    private const val CHANNEL_ID = "wakemove_updates"
    private const val NOTIFICATION_ID = 1401

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "应用更新",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "WakeMove 新版本提醒"
        }
        manager.createNotificationChannel(channel)
    }

    fun showAvailable(context: Context, info: AppUpdateInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_SHOW_UPDATE, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("WakeMove v${info.versionName} 可以更新")
            .setContentText("点击查看改进内容并安装")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }
}
