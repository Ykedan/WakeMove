package com.wakemove.android.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID) ?: return
        val dependencies = context.applicationContext as? SchedulingDependencies
        if (dependencies == null) {
            ContextCompat.startForegroundService(
                context,
                Intent(ACTION_START_RINGING)
                    .setClassName(context, "com.wakemove.android.ringing.RingingService")
                    .putExtra(EXTRA_ALARM_ID, alarmId),
            )
            return
        }
        val scheduledAtMillis = intent.getLongExtra(
            EXTRA_SCHEDULED_AT_MILLIS,
            System.currentTimeMillis(),
        ).takeIf { it > 0L } ?: System.currentTimeMillis()
        val pendingResult = goAsync()
        if (pendingResult == null) {
            runBlocking {
                dependencies.alarmDeliveryCoordinator.deliver(
                    alarmId = alarmId,
                    scheduledAt = Instant.ofEpochMilli(scheduledAtMillis),
                )
            }
            return
        }
        receiverScope.launch {
            try {
                dependencies.alarmDeliveryCoordinator.deliver(
                    alarmId = alarmId,
                    scheduledAt = Instant.ofEpochMilli(scheduledAtMillis),
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_ALARM_FIRED = "com.wakemove.android.action.ALARM_FIRED"
        const val ACTION_START_RINGING = "com.wakemove.android.action.START_RINGING"
        const val EXTRA_ALARM_ID = "com.wakemove.android.extra.ALARM_ID"
        const val EXTRA_SCHEDULED_AT_MILLIS =
            "com.wakemove.android.extra.SCHEDULED_AT_MILLIS"
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
