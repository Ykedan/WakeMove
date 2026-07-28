package com.wakemove.android.ringing

import com.wakemove.android.domain.Alarm
import com.wakemove.android.domain.AlarmEvent
import com.wakemove.android.domain.AlarmEventResult
import com.wakemove.android.domain.AlarmRepository
import com.wakemove.android.domain.ChallengeType
import com.wakemove.android.domain.PendingAlarmSchedule
import com.wakemove.android.domain.RingingSession
import com.wakemove.android.domain.SessionStatus
import com.wakemove.android.scheduling.AlarmScheduler
import com.wakemove.android.scheduling.PendingScheduleRecovery
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RingingSessionControllerTest {
    private val now = Instant.parse("2026-07-24T23:30:00.123456789Z")
    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock = Clock.fixed(now, zone)
    private val alarm = alarm()

    @Test
    fun `start persists the ringing session before playback and exposes it`() = runBlocking {
        val order = mutableListOf<String>()
        val repository = FakeAlarmRepository(alarm, order)
        val audioPlayer = FakeAlarmAudioPlayer(order)
        val vibrator = FakeAlarmVibrator(order)
        val controller = controller(repository, audioPlayer, vibrator)

        assertTrue(controller.start(alarm.id))

        val state = controller.state.value
        assertEquals(listOf("persist", "play", "vibrate"), order)
        assertEquals(alarm, state.alarm)
        assertEquals(SessionStatus.RINGING, state.session?.status)
        assertEquals(now, state.session?.scheduledAt)
        assertEquals(now, state.session?.startedAt)
        assertEquals(AlarmSoundState.PLAYING, state.soundState)
        assertEquals(alarm.snoozeLimit, state.remainingSnoozes)
    }

    @Test
    fun `snooze increments to three and then refuses another snooze`() = runBlocking {
        val repository = FakeAlarmRepository(alarm)
        val audioPlayer = FakeAlarmAudioPlayer()
        val vibrator = FakeAlarmVibrator()
        val scheduler = FakeAlarmScheduler()
        val controller = controller(repository, audioPlayer, vibrator, scheduler)

        repeat(alarm.snoozeLimit) { expectedPreviousCount ->
            assertTrue(controller.start(alarm.id))
            assertEquals(expectedPreviousCount, controller.state.value.session?.snoozeCount)
            assertTrue(controller.snooze())
            assertEquals(
                expectedPreviousCount + 1,
                controller.state.value.session?.snoozeCount,
            )
        }
        assertTrue(controller.start(alarm.id))

        assertFalse(controller.snooze())
        assertEquals(alarm.snoozeLimit, controller.state.value.session?.snoozeCount)
        assertEquals(0, controller.state.value.remainingSnoozes)
        assertEquals(alarm.snoozeLimit, scheduler.scheduled.size)
        assertTrue(scheduler.scheduled.all { it.second == now.plusSeconds(5 * 60L) })
    }

    @Test
    fun `stored snooze limit above three is still capped at three`() = runBlocking {
        val alarm = alarm(snoozeLimit = 10)
        val repository = FakeAlarmRepository(alarm)
        val controller = controller(
            repository = repository,
            audioPlayer = FakeAlarmAudioPlayer(),
            vibrator = FakeAlarmVibrator(),
        )

        repeat(3) {
            assertTrue(controller.start(alarm.id))
            assertTrue(controller.snooze())
        }
        assertTrue(controller.start(alarm.id))

        assertFalse(controller.snooze())
        assertEquals(3, repository.session?.snoozeCount)
        assertEquals(0, controller.state.value.remainingSnoozes)
    }

    @Test
    fun `failed snooze registration keeps the alarm ringing and can retry`() = runBlocking {
        val repository = FakeAlarmRepository(alarm)
        val scheduler = FakeAlarmScheduler(fail = true)
        val audioPlayer = FakeAlarmAudioPlayer()
        val vibrator = FakeAlarmVibrator()
        val controller = controller(repository, audioPlayer, vibrator, scheduler)
        controller.start(alarm.id)

        assertFalse(controller.snooze())

        val trigger = now.plusSeconds(5 * 60L)
        assertEquals(SessionStatus.RINGING, repository.session?.status)
        assertEquals(0, repository.session?.snoozeCount)
        assertEquals(null, repository.session?.pendingScheduleAt)
        assertEquals(1, scheduler.attempts)
        assertEquals(AlarmSoundState.PLAYING, audioPlayer.soundState)
        assertEquals(0, vibrator.stopCount)
        assertNotNull(controller.state.value.recoverableError)

        scheduler.fail = false
        assertTrue(controller.snooze())

        assertEquals(2, scheduler.attempts)
        assertEquals(listOf(alarm to trigger), scheduler.scheduled)
        assertEquals(SessionStatus.SNOOZED, repository.session?.status)
        assertEquals(null, repository.session?.pendingScheduleAt)
        assertEquals(AlarmSoundState.STOPPED, audioPlayer.soundState)
        assertEquals(1, vibrator.stopCount)
    }

    @Test
    fun `overlapping ringing delivery atomically records missed and starts the new alarm`() =
        runBlocking {
            val second = alarm().copy(id = "second-alarm", label = "Second")
            val repository = CollisionAlarmRepository(listOf(alarm, second))
            val audioPlayer = FakeAlarmAudioPlayer()
            val vibrator = FakeAlarmVibrator()
            val scheduler = FakeAlarmScheduler()
            val controller = RingingSessionController(
                repository = repository,
                audioPlayer = audioPlayer,
                vibrator = vibrator,
                scheduler = scheduler,
                clock = clock,
                zoneProvider = { zone },
                sessionIdFactory = sequenceOf("first-session", "second-session").iterator()::next,
            )

            assertTrue(controller.start(alarm.id))
            assertTrue(controller.start(second.id))

            assertEquals(second.id, controller.state.value.session?.alarmId)
            assertEquals(SessionStatus.RINGING, controller.state.value.session?.status)
            assertEquals(
                listOf(alarm.id to AlarmEventResult.MISSED),
                repository.events.map { it.alarmId to it.result },
            )
            assertEquals(SessionStatus.MISSED, repository.sessions["first-session"]?.status)
            assertEquals(SessionStatus.RINGING, repository.sessions["second-session"]?.status)
            assertEquals(AlarmSoundState.PLAYING, audioPlayer.soundState)
            assertEquals(
                listOf(alarm.id to Instant.parse("2026-07-30T23:30:00Z")),
                scheduler.scheduled.map { it.first.id to it.second },
            )
        }

    @Test
    fun `overlapping delivery replaces a snoozed session durably`() = runBlocking {
        val second = alarm().copy(id = "second-alarm", label = "Second")
        val repository = CollisionAlarmRepository(listOf(alarm, second))
        val controller = RingingSessionController(
            repository = repository,
            audioPlayer = FakeAlarmAudioPlayer(),
            vibrator = FakeAlarmVibrator(),
            scheduler = FakeAlarmScheduler(),
            clock = clock,
            zoneProvider = { zone },
            sessionIdFactory = sequenceOf("first-session", "second-session").iterator()::next,
        )
        assertTrue(controller.start(alarm.id))
        assertTrue(controller.snooze())

        assertTrue(controller.start(second.id))

        assertEquals(second.id, repository.activeSession()?.alarmId)
        assertEquals(SessionStatus.MISSED, repository.sessions["first-session"]?.status)
        assertEquals(AlarmEventResult.MISSED, repository.events.single().result)
    }

    @Test
    fun `repeat registration retries after schedule succeeds before acknowledge`() = runBlocking {
        val repository = FakeAlarmRepository(alarm).apply {
            acknowledgeSchedules = false
        }
        val scheduler = FakeAlarmScheduler()
        val controller = controller(
            repository,
            FakeAlarmAudioPlayer(),
            FakeAlarmVibrator(),
            scheduler,
        )
        controller.start(alarm.id)

        assertTrue(controller.complete())

        val trigger = Instant.parse("2026-07-30T23:30:00Z")
        assertEquals(trigger, repository.session?.pendingScheduleAt)
        assertEquals(listOf(alarm to trigger), scheduler.scheduled)

        repository.acknowledgeSchedules = true
        PendingScheduleRecovery(repository, scheduler).recover()

        assertEquals(listOf(alarm to trigger, alarm to trigger), scheduler.scheduled)
        assertEquals(null, repository.session?.pendingScheduleAt)
    }

    @Test
    fun `repeat scheduler exception remains durable for later recovery`() = runBlocking {
        val repository = FakeAlarmRepository(alarm)
        val scheduler = FakeAlarmScheduler(fail = true)
        val audioPlayer = FakeAlarmAudioPlayer()
        val controller = controller(
            repository,
            audioPlayer,
            FakeAlarmVibrator(),
            scheduler,
        )
        controller.start(alarm.id)

        assertTrue(controller.complete())
        val trigger = Instant.parse("2026-07-30T23:30:00Z")
        assertEquals(SessionStatus.COMPLETED, repository.session?.status)
        assertEquals(trigger, repository.session?.pendingScheduleAt)
        assertEquals(AlarmSoundState.STOPPED, audioPlayer.soundState)

        scheduler.fail = false
        val result = PendingScheduleRecovery(repository, scheduler).recover()
        assertEquals(1, result.registeredCount)
        assertEquals(null, repository.session?.pendingScheduleAt)
    }

    @Test
    fun `one shot completion disables the alarm without scheduling it again`() = runBlocking {
        val oneShot = alarm(repeatDays = emptySet())
        val repository = FakeAlarmRepository(oneShot)
        val scheduler = FakeAlarmScheduler()
        val controller = controller(
            repository,
            FakeAlarmAudioPlayer(),
            FakeAlarmVibrator(),
            scheduler,
        )
        controller.start(oneShot.id)

        assertTrue(controller.complete())

        assertEquals(false, repository.alarm?.enabled)
        assertEquals(SessionStatus.COMPLETED, repository.session?.status)
        assertEquals(null, repository.session?.pendingScheduleAt)
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun `complete records COMPLETED and reschedules a repeating alarm exactly once`() =
        runBlocking {
            val repository = FakeAlarmRepository(alarm)
            val audioPlayer = FakeAlarmAudioPlayer()
            val vibrator = FakeAlarmVibrator()
            val scheduler = FakeAlarmScheduler()
            val controller = controller(repository, audioPlayer, vibrator, scheduler)
            controller.start(alarm.id)

            assertTrue(controller.complete())
            assertFalse(controller.complete())

            assertEquals(SessionStatus.COMPLETED, repository.session?.status)
            assertEquals(listOf(AlarmEventResult.COMPLETED), repository.events.map { it.result })
            assertEquals(1, scheduler.scheduled.size)
            assertEquals(alarm, scheduler.scheduled.single().first)
            assertEquals(
                Instant.parse("2026-07-30T23:30:00Z"),
                scheduler.scheduled.single().second,
            )
            assertEquals(1, audioPlayer.stopCount)
            assertEquals(1, vibrator.stopCount)
        }

    @Test
    fun `bypass records BYPASSED and is idempotent`() = runBlocking {
        val repository = FakeAlarmRepository(alarm)
        val audioPlayer = FakeAlarmAudioPlayer()
        val vibrator = FakeAlarmVibrator()
        val controller = controller(repository, audioPlayer, vibrator)
        controller.start(alarm.id)

        assertTrue(controller.bypass())
        assertFalse(controller.bypass())

        val event = repository.events.single()
        assertEquals(SessionStatus.BYPASSED, repository.session?.status)
        assertEquals(AlarmEventResult.BYPASSED, event.result)
        assertEquals(now, event.finishedAt)
        assertEquals(repository.session?.id, event.id)
        assertEquals(1, audioPlayer.stopCount)
        assertEquals(1, vibrator.stopCount)
    }

    @Test
    fun `missing or disabled alarm does not start playback`() = runBlocking {
        val repository = FakeAlarmRepository(alarm.copy(enabled = false))
        val audioPlayer = FakeAlarmAudioPlayer()
        val vibrator = FakeAlarmVibrator()
        val controller = controller(repository, audioPlayer, vibrator)

        assertFalse(controller.start(alarm.id))

        assertEquals(null, repository.session)
        assertEquals(AlarmSoundState.STOPPED, audioPlayer.soundState)
        assertEquals(0, vibrator.startCount)
    }

    private fun controller(
        repository: FakeAlarmRepository,
        audioPlayer: FakeAlarmAudioPlayer,
        vibrator: FakeAlarmVibrator,
        scheduler: FakeAlarmScheduler = FakeAlarmScheduler(),
    ) = RingingSessionController(
        repository = repository,
        audioPlayer = audioPlayer,
        vibrator = vibrator,
        scheduler = scheduler,
        clock = clock,
        zoneProvider = { zone },
        sessionIdFactory = { "session-id" },
    )

    private fun alarm(
        snoozeLimit: Int = 3,
        repeatDays: Set<DayOfWeek> = setOf(DayOfWeek.FRIDAY),
    ) = Alarm(
        id = "alarm-id",
        time = LocalTime.of(7, 30),
        label = "Morning",
        enabled = true,
        repeatDays = repeatDays,
        soundId = "default",
        vibrationEnabled = true,
        snoozeMinutes = 5,
        snoozeLimit = snoozeLimit,
        challengeType = ChallengeType.SQUAT,
        targetCount = 10,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
}

private class CollisionAlarmRepository(
    alarms: List<Alarm>,
) : AlarmRepository {
    private val alarms = alarms.associateByTo(mutableMapOf(), Alarm::id)
    val sessions = linkedMapOf<String, RingingSession>()
    val events = mutableListOf<AlarmEvent>()

    override fun observeAlarms(): Flow<List<Alarm>> = flowOf(alarms.values.toList())
    override suspend fun upsertAlarm(alarm: Alarm) {
        alarms[alarm.id] = alarm
    }
    override suspend fun deleteAlarm(id: String) {
        alarms.remove(id)
    }
    override suspend fun getAlarm(id: String): Alarm? = alarms[id]
    override suspend fun saveSession(session: RingingSession) {
        sessions[session.id] = session
    }
    override suspend fun activeSession(): RingingSession? =
        sessions.values.lastOrNull { it.status in setOf(SessionStatus.RINGING, SessionStatus.SNOOZED) }
    override suspend fun transitionSession(
        session: RingingSession,
        expectedStatuses: Set<SessionStatus>,
        event: AlarmEvent?,
        alarmUpdate: Alarm?,
    ): Boolean {
        val current = sessions[session.id] ?: return false
        if (current.status !in expectedStatuses) return false
        sessions[session.id] = session
        event?.let(events::add)
        alarmUpdate?.let { alarms[it.id] = it }
        return true
    }
    override suspend fun replaceActiveSession(
        previous: RingingSession,
        expectedStatuses: Set<SessionStatus>,
        previousEvent: AlarmEvent,
        previousAlarmUpdate: Alarm?,
        next: RingingSession,
    ): Boolean {
        val current = sessions[previous.id] ?: return false
        if (current.status !in expectedStatuses) return false
        sessions[previous.id] = previous
        sessions[next.id] = next
        events += previousEvent
        previousAlarmUpdate?.let { alarms[it.id] = it }
        return true
    }
    override suspend fun pendingSchedules(): List<PendingAlarmSchedule> =
        sessions.values.mapNotNull { session ->
            session.pendingScheduleAt?.let { PendingAlarmSchedule(session.id, session.alarmId, it) }
        }
    override suspend fun acknowledgePendingSchedule(
        sessionId: String,
        scheduledAt: Instant,
    ): Boolean {
        val current = sessions[sessionId] ?: return false
        if (current.pendingScheduleAt != scheduledAt) return false
        sessions[sessionId] = current.copy(pendingScheduleAt = null)
        return true
    }
    override suspend fun appendEvent(event: AlarmEvent) {
        events += event
    }
    override suspend fun recentEvents(limit: Int): List<AlarmEvent> = events.take(limit)
    override suspend fun clearHistory() {
        sessions.clear()
        events.clear()
    }
}

private class FakeAlarmRepository(
    alarm: Alarm?,
    private val order: MutableList<String> = mutableListOf(),
) : AlarmRepository {
    var alarm: Alarm? = alarm
        private set
    var session: RingingSession? = null
        private set
    val events = mutableListOf<AlarmEvent>()
    var acknowledgeSchedules = true

    override fun observeAlarms(): Flow<List<Alarm>> = flowOf(listOfNotNull(alarm))

    override suspend fun upsertAlarm(alarm: Alarm) = error("not used")

    override suspend fun deleteAlarm(id: String) = error("not used")

    override suspend fun getAlarm(id: String): Alarm? = alarm?.takeIf { it.id == id }

    override suspend fun saveSession(session: RingingSession) {
        order += "persist"
        this.session = session
    }

    override suspend fun activeSession(): RingingSession? =
        session?.takeIf { it.status == SessionStatus.RINGING || it.status == SessionStatus.SNOOZED }

    override suspend fun transitionSession(
        session: RingingSession,
        expectedStatuses: Set<SessionStatus>,
        event: AlarmEvent?,
        alarmUpdate: Alarm?,
    ): Boolean {
        val current = this.session ?: return false
        if (current.id != session.id || current.status !in expectedStatuses) return false
        this.session = session
        if (event != null) events += event
        if (alarmUpdate != null) alarm = alarmUpdate
        return true
    }

    override suspend fun pendingSchedules(): List<PendingAlarmSchedule> =
        listOfNotNull(
            session?.pendingScheduleAt?.let { scheduledAt ->
                PendingAlarmSchedule(
                    sessionId = checkNotNull(session).id,
                    alarmId = checkNotNull(session).alarmId,
                    scheduledAt = scheduledAt,
                )
            },
        )

    override suspend fun acknowledgePendingSchedule(
        sessionId: String,
        scheduledAt: Instant,
    ): Boolean {
        val current = session ?: return false
        if (!acknowledgeSchedules ||
            current.id != sessionId ||
            current.pendingScheduleAt != scheduledAt
        ) {
            return false
        }
        session = current.copy(pendingScheduleAt = null)
        return true
    }

    override suspend fun appendEvent(event: AlarmEvent) {
        events += event
    }

    override suspend fun recentEvents(limit: Int): List<AlarmEvent> = events.take(limit)

    override suspend fun clearHistory() {
        session = null
        events.clear()
    }
}

private class FakeAlarmAudioPlayer(
    private val order: MutableList<String> = mutableListOf(),
) : AlarmAudioPlayer {
    override var soundState: AlarmSoundState = AlarmSoundState.STOPPED
        private set
    var stopCount = 0
        private set

    override fun play(soundId: String) {
        order += "play"
        soundState = AlarmSoundState.PLAYING
    }

    override fun stop() {
        stopCount += 1
        soundState = AlarmSoundState.STOPPED
    }
}

private class FakeAlarmVibrator(
    private val order: MutableList<String> = mutableListOf(),
) : AlarmVibrator {
    var startCount = 0
        private set
    var stopCount = 0
        private set

    override fun start() {
        order += "vibrate"
        startCount += 1
    }

    override fun stop() {
        stopCount += 1
    }
}

private class FakeAlarmScheduler(
    var fail: Boolean = false,
) : AlarmScheduler {
    val scheduled = mutableListOf<Pair<Alarm, Instant>>()
    var attempts = 0
        private set

    override fun schedule(alarm: Alarm, at: Instant) {
        attempts += 1
        if (fail) error("scheduler unavailable")
        scheduled += alarm to at
    }

    override fun cancel(alarmId: String) = error("not used")

    override suspend fun rescheduleAll() = error("not used")
}
