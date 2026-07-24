package com.wakemove.android.ringing

import com.wakemove.android.domain.Alarm
import com.wakemove.android.domain.AlarmEvent
import com.wakemove.android.domain.AlarmEventResult
import com.wakemove.android.domain.AlarmRepository
import com.wakemove.android.domain.ChallengeType
import com.wakemove.android.domain.RingingSession
import com.wakemove.android.domain.SessionStatus
import com.wakemove.android.scheduling.AlarmScheduler
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

    private fun alarm() = Alarm(
        id = "alarm-id",
        time = LocalTime.of(7, 30),
        label = "Morning",
        enabled = true,
        repeatDays = setOf(DayOfWeek.FRIDAY),
        soundId = "default",
        vibrationEnabled = true,
        snoozeMinutes = 5,
        snoozeLimit = 3,
        challengeType = ChallengeType.SQUAT,
        targetCount = 10,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
}

private class FakeAlarmRepository(
    private val alarm: Alarm?,
    private val order: MutableList<String> = mutableListOf(),
) : AlarmRepository {
    var session: RingingSession? = null
        private set
    val events = mutableListOf<AlarmEvent>()

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
    ): Boolean {
        val current = this.session ?: return false
        if (current.id != session.id || current.status !in expectedStatuses) return false
        this.session = session
        if (event != null) events += event
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

private class FakeAlarmScheduler : AlarmScheduler {
    val scheduled = mutableListOf<Pair<Alarm, Instant>>()

    override fun schedule(alarm: Alarm, at: Instant) {
        scheduled += alarm to at
    }

    override fun cancel(alarmId: String) = error("not used")

    override suspend fun rescheduleAll() = error("not used")
}
