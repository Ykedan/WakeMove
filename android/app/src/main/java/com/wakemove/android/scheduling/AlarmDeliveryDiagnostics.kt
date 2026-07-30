package com.wakemove.android.scheduling

import android.content.Context
import java.time.Clock
import java.time.Instant

enum class DeliveryStage {
    REGISTERED, DELIVERED, NEXT_REPEAT_REGISTERED, SERVICE_START_REQUESTED,
    SERVICE_STARTED, AUDIO_STARTED, RINGING, SNOOZED, COMPLETED, BYPASSED,
    MISSED, FAILED,
}

data class AlarmDeliveryRecord(
    val alarmId: String,
    val sessionId: String?,
    val scheduledAt: Instant,
    val stage: DeliveryStage,
    val stageAt: Instant,
    val nextRepeatAt: Instant?,
    val failureStage: DeliveryStage?,
    val failureClass: String?,
)

class AlarmDeliveryDiagnostics(
    context: Context,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "wakemove_alarm_delivery",
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun record(
        alarmId: String,
        scheduledAt: Instant,
        stage: DeliveryStage,
        sessionId: String? = null,
        nextRepeatAt: Instant? = null,
        failureStage: DeliveryStage? = null,
        failureClass: String? = null,
    ) {
        preferences.edit()
            .putString("alarm_id", alarmId)
            .putString("session_id", sessionId)
            .putLong("scheduled_at", scheduledAt.toEpochMilli())
            .putString("stage", stage.name)
            .putLong("stage_at", clock.instant().toEpochMilli())
            .apply {
                if (nextRepeatAt == null) remove("next_repeat_at")
                else putLong("next_repeat_at", nextRepeatAt.toEpochMilli())
            }
            .putString("failure_stage", failureStage?.name)
            .putString("failure_class", failureClass)
            .apply()
    }

    fun latest(): AlarmDeliveryRecord? {
        val alarmId = preferences.getString("alarm_id", null) ?: return null
        val stage = preferences.getString("stage", null)
            ?.let { runCatching { enumValueOf<DeliveryStage>(it) }.getOrNull() }
            ?: return null
        return AlarmDeliveryRecord(
            alarmId = alarmId,
            sessionId = preferences.getString("session_id", null),
            scheduledAt = Instant.ofEpochMilli(preferences.getLong("scheduled_at", 0L)),
            stage = stage,
            stageAt = Instant.ofEpochMilli(preferences.getLong("stage_at", 0L)),
            nextRepeatAt = preferences.takeIf { it.contains("next_repeat_at") }
                ?.let { Instant.ofEpochMilli(it.getLong("next_repeat_at", 0L)) },
            failureStage = preferences.getString("failure_stage", null)
                ?.let { runCatching { enumValueOf<DeliveryStage>(it) }.getOrNull() },
            failureClass = preferences.getString("failure_class", null),
        )
    }
}
