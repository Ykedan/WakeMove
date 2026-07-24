package com.wakemove.android.ringing

import com.wakemove.android.domain.Alarm
import com.wakemove.android.domain.AlarmEvent
import com.wakemove.android.domain.AlarmEventResult
import com.wakemove.android.domain.AlarmRepository
import com.wakemove.android.domain.RingingSession
import com.wakemove.android.domain.ScheduleCalculator
import com.wakemove.android.domain.SessionStatus
import com.wakemove.android.scheduling.AlarmScheduler
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
    private val scheduler: AlarmScheduler,
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
                val ringing = active.copy(status = SessionStatus.RINGING)
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
        if (session.snoozeCount >= alarm.snoozeLimit) return false

        val snoozed = session.copy(
            snoozeCount = session.snoozeCount + 1,
            status = SessionStatus.SNOOZED,
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
            scheduler.schedule(
                alarm,
                clock.instant().plusSeconds(alarm.snoozeMinutes * SECONDS_PER_MINUTE),
            )
        } finally {
            stopAlerting(alarm, snoozed)
        }
        true
    }

    suspend fun complete(): Boolean =
        finish(SessionStatus.COMPLETED, AlarmEventResult.COMPLETED)

    suspend fun bypass(): Boolean =
        finish(SessionStatus.BYPASSED, AlarmEventResult.BYPASSED)

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
        val terminal = session.copy(status = status)
        val event = AlarmEvent(
            id = session.id,
            alarmId = alarm.id,
            scheduledAt = session.scheduledAt,
            startedAt = session.startedAt,
            finishedAt = clock.instant(),
            challengeType = session.challengeType,
            snoozeCount = session.snoozeCount,
            result = result,
        )
        if (!repository.transitionSession(
                session = terminal,
                expectedStatuses = setOf(SessionStatus.RINGING),
                event = event,
            )
        ) {
            return false
        }

        mutableState.value = stateFor(alarm, terminal)
        try {
            scheduleNextRepeat(alarm)
        } finally {
            stopAlerting(alarm, terminal)
        }
        true
    }

    private fun scheduleNextRepeat(alarm: Alarm) {
        if (!alarm.enabled || alarm.repeatDays.isEmpty()) return
        val now = ZonedDateTime.ofInstant(clock.instant(), zoneProvider())
        val occurrence = ScheduleCalculator.nextOccurrence(alarm, now) ?: return
        scheduler.schedule(alarm, occurrence.toInstant())
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
        remainingSnoozes = (alarm.snoozeLimit - session.snoozeCount).coerceAtLeast(0),
    )

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
    }
}
