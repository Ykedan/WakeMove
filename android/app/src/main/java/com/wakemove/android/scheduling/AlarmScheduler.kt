package com.wakemove.android.scheduling

import com.wakemove.android.domain.Alarm
import java.time.Instant

interface AlarmScheduler {
    fun schedule(alarm: Alarm, at: Instant)

    fun cancel(alarmId: String)

    suspend fun rescheduleAll()

    fun onAlarmDelivered(alarmId: String) = Unit

    fun healthSnapshot(): SchedulerHealthSnapshot = SchedulerHealthSnapshot()
}

enum class SchedulingResult {
    NEVER,
    SUCCESS,
    FAILURE,
}

data class SchedulerHealthSnapshot(
    val lastResult: SchedulingResult = SchedulingResult.NEVER,
    val nextRegisteredAt: Instant? = null,
)

class ExactAlarmPermissionRequiredException :
    IllegalStateException("Exact alarm access is required to schedule WakeMove alarms")
