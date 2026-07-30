package com.wakemove.android.scheduling

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import java.time.Instant

class AlarmDeliveryCoordinator(
    private val context: Context,
    private val scheduler: AlarmScheduler,
    private val diagnostics: AlarmDeliveryDiagnostics,
    private val fallbackPublisher: (String) -> Unit =
        AlarmFallbackNotifier(context)::show,
    private val serviceStarter: (Context, Intent) -> Unit = { target, intent ->
        ContextCompat.startForegroundService(target, intent)
    },
) {
    suspend fun deliver(alarmId: String, scheduledAt: Instant) {
        scheduler.onAlarmDelivered(alarmId)
        diagnostics.record(alarmId, scheduledAt, DeliveryStage.DELIVERED)

        var nextRepeatFailure: Throwable? = null
        val nextRepeatAt = runCatching {
            scheduler.registerNextRepeatAfterDelivery(alarmId, scheduledAt)
        }.getOrElse { error ->
            nextRepeatFailure = error
            null
        }
        if (nextRepeatAt != null) {
            diagnostics.record(
                alarmId, scheduledAt, DeliveryStage.NEXT_REPEAT_REGISTERED,
                nextRepeatAt = nextRepeatAt,
            )
        }

        val serviceIntent = Intent(AlarmReceiver.ACTION_START_RINGING)
            .setClassName(context, "com.wakemove.android.ringing.RingingService")
            .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            .putExtra(AlarmReceiver.EXTRA_SCHEDULED_AT_MILLIS, scheduledAt.toEpochMilli())
        try {
            serviceStarter(context, serviceIntent)
            val repeatFailure = nextRepeatFailure
            if (repeatFailure == null) {
                diagnostics.record(
                    alarmId, scheduledAt, DeliveryStage.SERVICE_START_REQUESTED,
                    nextRepeatAt = nextRepeatAt,
                )
            } else {
                diagnostics.record(
                    alarmId, scheduledAt, DeliveryStage.FAILED,
                    failureStage = DeliveryStage.NEXT_REPEAT_REGISTERED,
                    failureClass = repeatFailure.javaClass.simpleName,
                )
            }
        } catch (error: RuntimeException) {
            diagnostics.record(
                alarmId, scheduledAt, DeliveryStage.FAILED,
                nextRepeatAt = nextRepeatAt,
                failureStage = DeliveryStage.SERVICE_START_REQUESTED,
                failureClass = error.javaClass.simpleName,
            )
            runCatching { fallbackPublisher(alarmId) }
        }
    }
}
