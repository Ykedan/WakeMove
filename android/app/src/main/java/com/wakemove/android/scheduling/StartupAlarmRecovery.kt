package com.wakemove.android.scheduling

import com.wakemove.android.domain.AlarmEvent
import com.wakemove.android.domain.AlarmEventResult
import com.wakemove.android.domain.AlarmRepository
import com.wakemove.android.domain.SessionStatus
import java.time.Clock
import java.time.Duration

class StartupAlarmRecovery(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val pendingScheduleRecovery: PendingScheduleRecovery,
    private val deliveryCoordinator: AlarmDeliveryCoordinator,
    private val diagnostics: AlarmDeliveryDiagnostics,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun recover() {
        val now = clock.instant()
        val active = repository.activeSession()
        if (active != null) {
            val target = active.pendingScheduleAt ?: active.startedAt
            val overdue = Duration.between(target, now)
            when {
                target.isAfter(now) && active.status == SessionStatus.SNOOZED ->
                    pendingScheduleRecovery.recover(active.id)

                !overdue.isNegative && overdue <= RECOVERY_WINDOW ->
                    deliveryCoordinator.deliver(active.alarmId, target)

                overdue > RECOVERY_WINDOW -> {
                    val alarm = repository.getAlarm(active.alarmId)
                    val terminal = active.copy(
                        status = SessionStatus.MISSED,
                        pendingScheduleAt = null,
                    )
                    val event = AlarmEvent(
                        id = "missed:${active.id}:${now.epochSecond}",
                        alarmId = active.alarmId,
                        scheduledAt = active.scheduledAt,
                        startedAt = active.startedAt,
                        finishedAt = now,
                        challengeType = active.challengeType,
                        snoozeCount = active.snoozeCount,
                        result = AlarmEventResult.MISSED,
                    )
                    repository.transitionSession(
                        session = terminal,
                        expectedStatuses = setOf(SessionStatus.RINGING, SessionStatus.SNOOZED),
                        event = event,
                        alarmUpdate = alarm
                            ?.takeIf { it.repeatDays.isEmpty() }
                            ?.copy(enabled = false, updatedAt = now),
                    )
                    diagnostics.record(
                        active.alarmId,
                        active.scheduledAt,
                        DeliveryStage.MISSED,
                        sessionId = active.id,
                    )
                }
            }
        }
        pendingScheduleRecovery.recover()
        scheduler.rescheduleAll()
    }

    private companion object {
        val RECOVERY_WINDOW: Duration = Duration.ofMinutes(15)
    }
}
