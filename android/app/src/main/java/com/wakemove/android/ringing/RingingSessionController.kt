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
)

class RingingSessionController(
    private val repository: AlarmRepository,
    private val audioPlayer: AlarmAudioPlayer,
    private val vibrator: AlarmVibrator,
    scheduler: AlarmScheduler,
    private val pendingScheduleRecovery: PendingScheduleRecovery =
        PendingScheduleRecovery(repository, scheduler),
    private val clock: Clock = Clock.systemUTC(),
    private val zoneProvider: () -> ZoneId = ZoneId::systemDefault,
    private val sessionIdFactory: () -> String = { UUID.randomUUID().toString() },
) {
    private val transitionMutex = Mutex()
    private val mutableState = MutableStateFlow(RingingUiState())

    val state: StateFlow<RingingUiState> = mutableState.asStateFlow()

    suspend fun start(alarmId: String): Boolean = transitionMutex.withLock {
        val alarm = repository.getAlarm(alarmId)?.takeIf(Alarm::enabled) ?: return false
        val active = repository.activeSession()
        val session = when {
            active == null -> {
                val now = clock.instant()
                RingingSession(
                    id = sessionIdFactory(),
                    alarmId = alarm.id,
                    scheduledAt = now,
                    startedAt = now,
                    snoozeCount = 0,
                    challengeType = alarm.challengeType,
                    targetCount = alarm.targetCount,
                    status = SessionStatus.RINGING,
                ).also { repository.saveSession(it) }
            }

            active.alarmId != alarm.id -> return false
            active.status == SessionStatus.SNOOZED -> {
                val ringing = active.copy(
                    status = SessionStatus.RINGING,
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

        mutableState.value = stateFor(alarm, snoozed)
        try {
            pendingScheduleRecovery.recover(snoozed.id)
        } finally {
            stopAlerting(alarm, snoozed)
        }
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

    private fun stateFor(alarm: Alarm, session: RingingSession) = RingingUiState(
        alarm = alarm,
        session = session,
        soundState = audioPlayer.soundState,
        remainingSnoozes = (
            effectiveSnoozeLimit(alarm) - session.snoozeCount
        ).coerceAtLeast(0),
    )

    private fun effectiveSnoozeLimit(alarm: Alarm): Int =
        alarm.snoozeLimit.coerceIn(0, MAX_SNOOZE_COUNT)

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
    }
}
