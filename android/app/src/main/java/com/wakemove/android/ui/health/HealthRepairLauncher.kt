package com.wakemove.android.ui.health

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

fun launchHealthRepair(context: Context, issue: HealthIssue) {
    val packageUri = "package:${context.packageName}".toUri()
    val intent = when (issue) {
        HealthIssue.EXACT_ALARM -> Intent(
            ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            packageUri,
        )
        HealthIssue.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        HealthIssue.FULL_SCREEN_INTENT -> Intent(
            ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            packageUri,
        )
        HealthIssue.CAMERA,
        HealthIssue.MICROPHONE,
        -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
    }
}

private const val ACTION_REQUEST_SCHEDULE_EXACT_ALARM =
    "android.settings.REQUEST_SCHEDULE_EXACT_ALARM"
private const val ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT =
    "android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT"
