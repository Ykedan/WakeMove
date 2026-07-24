package com.wakemove.android.data

import androidx.room.Room
import com.wakemove.android.domain.Alarm
import com.wakemove.android.domain.AlarmEvent
import com.wakemove.android.domain.AlarmEventResult
import com.wakemove.android.domain.ChallengeType
import com.wakemove.android.domain.PendingAlarmSchedule
import com.wakemove.android.domain.RingingSession
import com.wakemove.android.domain.SessionStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class RoomAlarmRepositoryTest {
    private lateinit var database: AlarmDatabase
    private lateinit var repository: RoomAlarmRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AlarmDatabase::class.java,
        ).build()
        repository = RoomAlarmRepository(database.alarmDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `alarms round trip through Room and are ordered by time`() = runBlocking {
        val later = alarm(
            id = "later",
            time = LocalTime.of(8, 45),
            repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY),
            challengeType = ChallengeType.JUMPING_JACK,
        )
        val earlier = alarm(
            id = "earlier",
            time = LocalTime.of(6, 15),
            repeatDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY),
            challengeType = ChallengeType.VOICE_PHRASE,
        )

        repository.upsertAlarm(later)
        repository.upsertAlarm(earlier)

        assertEquals(earlier, repository.getAlarm(earlier.id))
        assertEquals(later, repository.getAlarm(later.id))
        assertEquals(listOf(earlier, later), repository.observeAlarms().first())
    }

    @Test
    fun `active session is recovered until it reaches a terminal status`() = runBlocking {
        val ringing = session(status = SessionStatus.RINGING)

        repository.saveSession(ringing)

        assertEquals(ringing, repository.activeSession())

        repository.saveSession(ringing.copy(status = SessionStatus.COMPLETED))

        assertNull(repository.activeSession())
    }

    @Test
    fun `terminal transition and event append happen once`() = runBlocking {
        val ringing = session(status = SessionStatus.RINGING)
        val terminal = ringing.copy(status = SessionStatus.COMPLETED)
        val completed = event(
            id = ringing.id,
            alarmId = ringing.alarmId,
            scheduledAt = ringing.scheduledAt,
            startedAt = ringing.startedAt,
        )
        repository.saveSession(ringing)

        assertEquals(
            true,
            repository.transitionSession(
                session = terminal,
                expectedStatuses = setOf(SessionStatus.RINGING),
                event = completed,
            ),
        )
        assertEquals(
            false,
            repository.transitionSession(
                session = terminal,
                expectedStatuses = setOf(SessionStatus.RINGING),
                event = completed,
            ),
        )

        assertNull(repository.activeSession())
        assertEquals(listOf(completed), repository.recentEvents())
    }

    @Test
    fun `pending schedule round trips losslessly and is acknowledged by exact value`() =
        runBlocking {
            val scheduledAt = Instant.ofEpochSecond(1_800_000_010, 987_654_321)
            val ringing = session().copy(pendingScheduleAt = scheduledAt)
            repository.saveSession(ringing)

            assertEquals(
                listOf(
                    PendingAlarmSchedule(
                        sessionId = ringing.id,
                        alarmId = ringing.alarmId,
                        scheduledAt = scheduledAt,
                    ),
                ),
                repository.pendingSchedules(),
            )
            assertEquals(
                false,
                repository.acknowledgePendingSchedule(
                    ringing.id,
                    scheduledAt.minusNanos(1),
                ),
            )
            assertEquals(listOf(scheduledAt), repository.pendingSchedules().map { it.scheduledAt })

            assertEquals(
                true,
                repository.acknowledgePendingSchedule(ringing.id, scheduledAt),
            )
            assertEquals(emptyList<PendingAlarmSchedule>(), repository.pendingSchedules())
        }

    @Test
    fun `terminal transition atomically disables a one shot alarm`() = runBlocking {
        val oneShot = alarm(id = "one-shot", repeatDays = emptySet())
        val ringing = session(alarmId = oneShot.id)
        val terminal = ringing.copy(status = SessionStatus.COMPLETED)
        val completed = event(id = ringing.id, alarmId = oneShot.id)
        repository.upsertAlarm(oneShot)
        repository.saveSession(ringing)

        assertEquals(
            true,
            repository.transitionSession(
                session = terminal,
                expectedStatuses = setOf(SessionStatus.RINGING),
                event = completed,
                alarmUpdate = oneShot.copy(enabled = false),
            ),
        )

        assertEquals(false, repository.getAlarm(oneShot.id)?.enabled)
        assertNull(repository.activeSession())
        assertEquals(listOf(completed), repository.recentEvents())
    }

    @Test
    fun `persisted snooze limit is clamped to zero through three`() = runBlocking {
        val aboveMaximum = alarm(id = "above", snoozeLimit = 10)
        val belowMinimum = alarm(id = "below", snoozeLimit = -4)

        repository.upsertAlarm(aboveMaximum)
        repository.upsertAlarm(belowMinimum)

        assertEquals(3, repository.getAlarm(aboveMaximum.id)?.snoozeLimit)
        assertEquals(0, repository.getAlarm(belowMinimum.id)?.snoozeLimit)
    }

    @Test
    fun `deleting an alarm retains its ringing session and event history`() = runBlocking {
        val alarm = alarm(id = "alarm-with-history")
        val session = session(alarmId = alarm.id, status = SessionStatus.SNOOZED)
        val event = event(alarmId = alarm.id)
        repository.upsertAlarm(alarm)
        repository.saveSession(session)
        repository.appendEvent(event)

        repository.deleteAlarm(alarm.id)

        assertNull(repository.getAlarm(alarm.id))
        assertEquals(session, repository.activeSession())
        assertEquals(listOf(event), repository.recentEvents())
    }

    @Test
    fun `recent events are newest first and respect the requested limit`() = runBlocking {
        val older = event(
            id = "older",
            scheduledAt = Instant.parse("2026-01-01T06:00:00Z"),
        )
        val newer = event(
            id = "newer",
            scheduledAt = Instant.parse("2026-01-02T06:00:00Z"),
        )
        repository.appendEvent(older)
        repository.appendEvent(newer)

        assertEquals(listOf(newer), repository.recentEvents(limit = 1))
    }

    @Test
    fun `domain instants round trip without losing nanoseconds`() = runBlocking {
        val alarm = alarm(
            id = "nanosecond-alarm",
            createdAt = Instant.ofEpochSecond(1_800_000_000, 123_456_789),
            updatedAt = Instant.ofEpochSecond(1_800_000_001, 987_654_321),
        )
        val session = session(
            id = "nanosecond-session",
            alarmId = alarm.id,
            scheduledAt = Instant.ofEpochSecond(1_800_000_002, 234_567_891),
            startedAt = Instant.ofEpochSecond(1_800_000_003, 345_678_912),
        )
        val event = event(
            id = "nanosecond-event",
            alarmId = alarm.id,
            scheduledAt = Instant.ofEpochSecond(1_800_000_004, 456_789_123),
            startedAt = Instant.ofEpochSecond(1_800_000_005, 567_891_234),
            finishedAt = Instant.ofEpochSecond(1_800_000_006, 678_912_345),
        )

        repository.upsertAlarm(alarm)
        repository.saveSession(session)
        repository.appendEvent(event)

        assertEquals(alarm, repository.getAlarm(alarm.id))
        assertEquals(session, repository.activeSession())
        assertEquals(listOf(event), repository.recentEvents())
    }

    @Test
    fun `same second timestamps are ordered by nanoseconds`() = runBlocking {
        val olderInstant = Instant.ofEpochSecond(1_800_000_000, 100)
        val newerInstant = Instant.ofEpochSecond(1_800_000_000, 900)
        val olderAlarm = alarm(
            id = "z-older-alarm",
            createdAt = olderInstant,
            updatedAt = olderInstant,
        )
        val newerAlarm = alarm(
            id = "a-newer-alarm",
            createdAt = newerInstant,
            updatedAt = newerInstant,
        )
        val olderSession = session(
            id = "z-older-session",
            startedAt = olderInstant,
        )
        val newerSession = session(
            id = "a-newer-session",
            startedAt = newerInstant,
        )
        val olderEvent = event(
            id = "z-older-event",
            scheduledAt = olderInstant,
        )
        val newerEvent = event(
            id = "a-newer-event",
            scheduledAt = newerInstant,
        )

        repository.upsertAlarm(newerAlarm)
        repository.upsertAlarm(olderAlarm)
        repository.saveSession(olderSession)
        repository.saveSession(newerSession)
        repository.appendEvent(olderEvent)
        repository.appendEvent(newerEvent)

        assertEquals(listOf(olderAlarm, newerAlarm), repository.observeAlarms().first())
        assertEquals(newerSession, repository.activeSession())
        assertEquals(listOf(newerEvent, olderEvent), repository.recentEvents())
    }

    @Test
    fun `negative event limit is rejected before querying Room`() {
        database.close()

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.recentEvents(limit = -1)
            }
        }
    }

    @Test
    fun `clear history removes sessions and events but keeps alarms`() = runBlocking {
        val alarm = alarm()
        repository.upsertAlarm(alarm)
        repository.saveSession(session(alarmId = alarm.id))
        repository.appendEvent(event(alarmId = alarm.id))

        repository.clearHistory()

        assertEquals(alarm, repository.getAlarm(alarm.id))
        assertNull(repository.activeSession())
        assertEquals(emptyList<AlarmEvent>(), repository.recentEvents())
    }

    private fun alarm(
        id: String = "alarm-1",
        time: LocalTime = LocalTime.of(7, 30),
        repeatDays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY),
        challengeType: ChallengeType = ChallengeType.SQUAT,
        snoozeLimit: Int = 2,
        createdAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt: Instant = Instant.parse("2026-01-02T00:00:00Z"),
    ) = Alarm(
        id = id,
        time = time,
        label = "Morning alarm",
        enabled = true,
        repeatDays = repeatDays,
        soundId = "gentle-rise",
        vibrationEnabled = true,
        snoozeMinutes = 7,
        snoozeLimit = snoozeLimit,
        challengeType = challengeType,
        targetCount = 12,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun session(
        id: String = "session-1",
        alarmId: String = "alarm-1",
        status: SessionStatus = SessionStatus.RINGING,
        scheduledAt: Instant = Instant.parse("2026-01-03T07:30:00Z"),
        startedAt: Instant = Instant.parse("2026-01-03T07:30:03Z"),
    ) = RingingSession(
        id = id,
        alarmId = alarmId,
        scheduledAt = scheduledAt,
        startedAt = startedAt,
        snoozeCount = 1,
        challengeType = ChallengeType.HANDS_UP,
        targetCount = 10,
        status = status,
    )

    private fun event(
        id: String = "event-1",
        alarmId: String = "alarm-1",
        scheduledAt: Instant = Instant.parse("2026-01-03T07:30:00Z"),
        startedAt: Instant? = scheduledAt.plusSeconds(3),
        finishedAt: Instant? = scheduledAt.plusSeconds(90),
    ) = AlarmEvent(
        id = id,
        alarmId = alarmId,
        scheduledAt = scheduledAt,
        startedAt = startedAt,
        finishedAt = finishedAt,
        challengeType = ChallengeType.HANDS_UP,
        snoozeCount = 1,
        result = AlarmEventResult.COMPLETED,
    )
}
