package com.wakemove.android.ringing

import com.wakemove.android.domain.Alarm
import com.wakemove.android.domain.AlarmEvent
import com.wakemove.android.domain.AlarmEventResult
import com.wakemove.android.domain.AlarmRepository
import com.wakemove.android.domain.MAX_SNOOZE_COUNT
import com.wakemove.android.domain.RingingSession
import com.wakemove.android.domain.ScheduleCalculator
import com.wakemove.android.domain.SessionStatus
import com.wakemove.android.scheduling.AlarmScheduler
import com.wakemove.android.scheduling.AlarmDeliveryDiagnostics
import com.wakemove.android.scheduling.DeliveryStage
import com.wakemove.android.scheduling.PendingScheduleRecovery
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AlarmVibrator {
    fun start()

    fun stop()
}

data class RingingUiState(
    val alarm: Alarm? = null,
    val session: RingingSession? = null,
    val soundState: AlarmSoundState = AlarmSoundState.STOPPED,
    val remainingSnoozes: Int = 0,
    val recoverableError: String? = null,
    val challengeRequested: Boolean = false,
)

class RingingSessionController(
    private val repository: AlarmRepository,
    private val audioPlayer: AlarmAudioPlayer,
    private val vibrator: AlarmVibrator,
    private val scheduler: AlarmScheduler,
    private val pendingScheduleRecovery: PendingScheduleRecovery =
        PendingScheduleRecovery(repository, scheduler),
    private val clock: Clock = Clock.systemUTC(),
    private val zoneProvider: () -> ZoneId = ZoneId::systemDefault,
    private val sessionIdFactory: () -> String = { UUID.randomUUID().toString() },
    private val deliveryDiagnostics: AlarmDeliveryDiagnostics? = null,
) {
    private val transitionMutex = Mutex()
    private val mutableState = MutableStateFlow(RingingUiState())

    val state: StateFlow<RingingUiState> = mutableState.asStateFlow()

    suspend fun start(alarmId: String): Boolean = transitionMutex.withLock {
        val alarm = repository.getAlarm(alarmId)?.takeIf(Alarm::enabled) ?: return false
        val active = repository.activeSession()
        val session = when {
            active == null -> {
                newSession(alarm).also { repository.saveSession(it) }
            }

            active.alarmId != alarm.id -> {
                val now = clock.instant()
                val previousAlarm = repository.getAlarm(active.alarmId)
                val previousNextAt = previousAlarm?.let { nextRepeatAt(it, now) }
                val previousTerminal = active.copy(
                    status = SessionStatus.MISSED,
                    pendingScheduleAt = previousNextAt,
                )
                val previousAlarmUpdate = previousAlarm
                    ?.takeIf { it.repeatDays.isEmpty() }
                    ?.copy(enabled = false, updatedAt = now)
                val previousEvent = AlarmEvent(
                    id = active.id,
                    alarmId = active.alarmId,
                    scheduledAt = active.scheduledAt,
                    startedAt = active.startedAt,
                    finishedAt = now,
                    challengeType = active.challengeType,
                    snoozeCount = active.snoozeCount,
                    result = AlarmEventResult.MISSED,
                )
                val next = newSession(alarm)
                val replaced = repository.replaceActiveSession(
                    previous = previousTerminal,
                    expectedStatuses = setOf(SessionStatus.RINGING, SessionStatus.SNOOZED),
                    previousEvent = previousEvent,
                    previousAlarmUpdate = previousAlarmUpdate,
                    next = next,
                )
                if (!replaced) return false
                next
            }
            active.status == SessionStatus.SNOOZED -> {
                val ringing = active.copy(
                    status = SessionStatus.RINGING,
                    startedAt = clock.instant(),
                    pendingScheduleAt = null,
                )
                val transitioned = repository.transitionSession(
                    session = ringing,
                    expectedStatuses = setOf(SessionStatus.SNOOZED),
                )
                if (!transitioned) return false
                ringing
            }

            active.status == SessionStatus.RINGING -> {
                if (mutableState.value.session?.id == active.id &&
                    audioPlayer.soundState == AlarmSoundState.PLAYING
                ) {
                    return false
                }
                active
            }

            else -> return false
        }

        mutableState.value = stateFor(alarm, session)
        audioPlayer.play(alarm.soundId)
        if (alarm.vibrationEnabled) vibrator.start()
        mutableState.value = stateFor(alarm, session)
        if (active != null && active.alarmId != alarm.id) {
            pendingScheduleRecovery.recover(active.id)
        }
        true
    }

    suspend fun snooze(): Boolean = transitionMutex.withLock {
        val current = mutableState.value
        val alarm = current.alarm ?: return false
        val session = current.session?.takeIf { it.status == SessionStatus.RINGING }
            ?: return false
        if (session.snoozeCount >= effectiveSnoozeLimit(alarm)) return false

        val trigger = clock.instant().plusSeconds(
            alarm.snoozeMinutes * SECONDS_PER_MINUTE,
        )
        val snoozed = session.copy(
            snoozeCount = session.snoozeCount + 1,
            status = SessionStatus.SNOOZED,
            pendingScheduleAt = trigger,
        )
        if (!repository.transitionSession(
                session = snoozed,
                expectedStatuses = setOf(SessionStatus.RINGING),
            )
        ) {
            return false
        }

        val recovery = pendingScheduleRecovery.recover(snoozed.id)
        if (recovery.failureCount > 0) {
            val restored = session.copy(pendingScheduleAt = null)
            repository.transitionSession(
                session = restored,
                expectedStatuses = setOf(SessionStatus.SNOOZED),
            )
            mutableState.value = stateFor(
                alarm = alarm,
                session = restored,
                recoverableError = "贪睡注册失败，闹钟会继续响铃，请重试",
            )
            return false
        }
        val persisted = repository.activeSession()
            ?.takeIf { it.id == snoozed.id && it.status == SessionStatus.SNOOZED }
            ?: snoozed.copy(pendingScheduleAt = null)
        stopAlerting(alarm, persisted)
        deliveryDiagnostics?.record(
            alarmId = alarm.id,
            scheduledAt = session.scheduledAt,
            stage = DeliveryStage.SNOOZED,
            sessionId = snoozed.id,
            nextRepeatAt = trigger,
        )
        true
    }

    suspend fun challengeNow(): Boolean = transitionMutex.withLock {
        val snoozed = repository.activeSession()
            ?.takeIf { it.status == SessionStatus.SNOOZED }
            ?: return false
        val alarm = repository.getAlarm(snoozed.alarmId)
            ?.takeIf(Alarm::enabled)
            ?: return false
        val ringing = snoozed.copy(
            status = SessionStatus.RINGING,
            startedAt = clock.instant(),
            pendingScheduleAt = null,
        )
        if (!repository.transitionSession(
                session = ringing,
                expectedStatuses = setOf(SessionStatus.SNOOZED),
            )
        ) {
            return false
        }

        runCatching { scheduler.cancel(alarm.id) }
        mutableState.value = stateFor(alarm, ringing).copy(challengeRequested = true)
        audioPlayer.play(alarm.soundId)
        if (alarm.vibrationEnabled) vibrator.start()
        mutableState.value = stateFor(alarm, ringing).copy(challengeRequested = true)
        deliveryDiagnostics?.record(
            alarmId = alarm.id,
            scheduledAt = ringing.scheduledAt,
            stage = DeliveryStage.RINGING,
            sessionId = ringing.id,
        )
        true
    }

    suspend fun complete(): Boolean =
        finish(SessionStatus.COMPLETED, AlarmEventResult.COMPLETED)

    suspend fun bypass(): Boolean =
        finish(SessionStatus.BYPASSED, AlarmEventResult.BYPASSED)

    suspend fun recoverPendingSchedules() = pendingScheduleRecovery.recover()

    internal fun releaseAlerting() {
        audioPlayer.stop()
        vibrator.stop()
        val current = mutableState.value
        val alarm = current.alarm ?: return
        val session = current.session ?: return
        mutableState.value = stateFor(alarm, session)
    }

    private suspend fun finish(
        status: SessionStatus,
        result: AlarmEventResult,
    ): Boolean = transitionMutex.withLock {
        val current = mutableState.value
        val alarm = current.alarm ?: return false
        val session = current.session?.takeIf { it.status == SessionStatus.RINGING }
            ?: return false
        val finishedAt = clock.instant()
        val nextRepeatAt = nextRepeatAt(alarm, finishedAt)
        val terminal = session.copy(
            status = status,
            pendingScheduleAt = nextRepeatAt,
        )
        val alarmUpdate = alarm
            .takeIf { it.repeatDays.isEmpty() }
            ?.copy(enabled = false, updatedAt = finishedAt)
        val event = AlarmEvent(
            id = session.id,
            alarmId = alarm.id,
            scheduledAt = session.scheduledAt,
            startedAt = session.startedAt,
            finishedAt = finishedAt,
            challengeType = session.challengeType,
            snoozeCount = session.snoozeCount,
            result = result,
        )
        if (!repository.transitionSession(
                session = terminal,
                expectedStatuses = setOf(SessionStatus.RINGING),
                event = event,
                alarmUpdate = alarmUpdate,
            )
        ) {
            return false
        }

        val currentAlarm = alarmUpdate ?: alarm
        mutableState.value = stateFor(currentAlarm, terminal)
        try {
            if (terminal.pendingScheduleAt != null) {
                pendingScheduleRecovery.recover(terminal.id)
            }
        } finally {
            stopAlerting(currentAlarm, terminal)
        }
        deliveryDiagnostics?.record(
            alarmId = alarm.id,
            scheduledAt = session.scheduledAt,
            stage = when (status) {
                SessionStatus.COMPLETED -> DeliveryStage.COMPLETED
                SessionStatus.BYPASSED -> DeliveryStage.BYPASSED
                else -> DeliveryStage.FAILED
            },
            sessionId = session.id,
            nextRepeatAt = nextRepeatAt,
        )
        true
    }

    private fun nextRepeatAt(alarm: Alarm, now: java.time.Instant): java.time.Instant? {
        if (!alarm.enabled || alarm.repeatDays.isEmpty()) return null
        val zonedNow = ZonedDateTime.ofInstant(now, zoneProvider())
        return ScheduleCalculator.nextOccurrence(alarm, zonedNow)?.toInstant()
    }

    private fun stopAlerting(alarm: Alarm, session: RingingSession) {
        audioPlayer.stop()
        vibrator.stop()
        mutableState.value = stateFor(alarm, session)
    }

    private fun stateFor(
        alarm: Alarm,
        session: RingingSession,
        recoverableError: String? = null,
    ) = RingingUiState(
        alarm = alarm,
        session = session,
        soundState = audioPlayer.soundState,
        remainingSnoozes = (
            effectiveSnoozeLimit(alarm) - session.snoozeCount
        ).coerceAtLeast(0),
        recoverableError = recoverableError,
    )

    private fun newSession(alarm: Alarm): RingingSession {
        val now = clock.instant()
        return RingingSession(
            id = sessionIdFactory(),
            alarmId = alarm.id,
            scheduledAt = now,
            startedAt = now,
            snoozeCount = 0,
            challengeType = alarm.challengeType,
            targetCount = alarm.targetCount,
            status = SessionStatus.RINGING,
        )
    }

    private fun effectiveSnoozeLimit(alarm: Alarm): Int =
        alarm.snoozeLimit.coerceIn(0, MAX_SNOOZE_COUNT)

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
    }
}
