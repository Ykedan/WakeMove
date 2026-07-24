package com.wakemove.android.scheduling

import com.wakemove.android.domain.AlarmRepository
import com.wakemove.android.domain.PendingAlarmSchedule

data class PendingScheduleRecoveryResult(
    val registeredCount: Int,
    val discardedCount: Int,
    val failures: List<PendingAlarmSchedule>,
) {
    val failureCount: Int
        get() = failures.size
}

class PendingScheduleRecovery(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
) {
    suspend fun recover(sessionId: String? = null): PendingScheduleRecoveryResult {
        var registeredCount = 0
        var discardedCount = 0
        val failures = mutableListOf<PendingAlarmSchedule>()
        repository.pendingSchedules()
            .asSequence()
            .filter { sessionId == null || it.sessionId == sessionId }
            .forEach { pending ->
                val alarm = repository.getAlarm(pending.alarmId)
                if (alarm == null || !alarm.enabled) {
                    try {
                        scheduler.cancel(pending.alarmId)
                        repository.acknowledgePendingSchedule(
                            pending.sessionId,
                            pending.scheduledAt,
                        )
                        discardedCount += 1
                    } catch (_: Exception) {
                        failures += pending
                    }
                    return@forEach
                }

                try {
                    scheduler.schedule(alarm, pending.scheduledAt)
                    registeredCount += 1
                    repository.acknowledgePendingSchedule(
                        pending.sessionId,
                        pending.scheduledAt,
                    )
                } catch (_: Exception) {
                    failures += pending
                }
            }
        return PendingScheduleRecoveryResult(
            registeredCount = registeredCount,
            discardedCount = discardedCount,
            failures = failures,
        )
    }
}
