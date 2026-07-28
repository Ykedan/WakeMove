package com.wakemove.android.scheduling

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import com.wakemove.android.domain.Alarm
import com.wakemove.android.domain.AlarmEvent
import com.wakemove.android.domain.AlarmEventResult
import com.wakemove.android.domain.AlarmRepository
import com.wakemove.android.domain.ChallengeType
import com.wakemove.android.domain.RingingSession
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
@Suppress("DEPRECATION")
class AndroidAlarmSchedulerTest {
    private val now = Instant.parse("2026-07-24T00:00:00Z")
    private val zone = ZoneId.of("Asia/Shanghai")
    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        alarmManager = context.getSystemService(AlarmManager::class.java)
        context.getSharedPreferences("wakemove_scheduler_diagnostics", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun `schedule uses an alarm clock and immutable broadcast with stable UUID request code`() {
        val alarm = alarm(id = "8c6856d8-9d26-4a13-8764-dca492a8966e")
        val trigger = now.plusSeconds(600)
        val scheduler = scheduler(listOf(alarm))

        scheduler.schedule(alarm, trigger)

        val scheduled = shadowOf(alarmManager).scheduledAlarms.single()
        val operation = shadowOf(scheduled.operation)
        val alarmClockInfo = checkNotNull(scheduled.alarmClockInfo)
        assertNotNull(alarmClockInfo)
        assertEquals(trigger.toEpochMilli(), alarmClockInfo.triggerTime)
        assertEquals(AlarmManager.RTC_WAKEUP, scheduled.type)
        assertTrue(operation.isBroadcast)
        assertTrue(operation.isImmutable)
        assertEquals(alarm.id.hashCode(), operation.requestCode)
        assertEquals(alarm.id, operation.savedIntent.getStringExtra(AlarmReceiver.EXTRA_ALARM_ID))
        assertEquals(
            AlarmReceiver::class.java.name,
            operation.savedIntent.component?.className,
        )
        assertEquals(
            SchedulerHealthSnapshot(
                lastResult = SchedulingResult.SUCCESS,
                nextRegisteredAt = trigger,
            ),
            scheduler.healthSnapshot(),
        )
    }

    @Test
    fun `cancel removes the matching scheduled alarm`() {
        val alarm = alarm(id = "37a6324f-24f1-49ad-aabb-cf6760f90e3c")
        val scheduler = scheduler(listOf(alarm))
        scheduler.schedule(alarm, now.plusSeconds(600))

        scheduler.cancel(alarm.id)

        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    @Test
    fun `colliding request codes retain distinct alarms and cancellation identities`() {
        val first = alarm(id = "FB")
        val second = alarm(id = "Ea")
        assertEquals(first.id.hashCode(), second.id.hashCode())
        val scheduler = scheduler(listOf(first, second))

        scheduler.schedule(first, now.plusSeconds(600))
        scheduler.schedule(second, now.plusSeconds(1_200))

        val scheduled = shadowOf(alarmManager).scheduledAlarms
        assertEquals(2, scheduled.size)
        assertEquals(
            setOf(first.id, second.id),
            scheduled.map {
                shadowOf(it.operation).savedIntent.getStringExtra(AlarmReceiver.EXTRA_ALARM_ID)
            }.toSet(),
        )
        assertEquals(
            2,
            scheduled.map { shadowOf(it.operation).savedIntent.data }.distinct().size,
        )

        scheduler.cancel(first.id)

        val remaining = shadowOf(alarmManager).scheduledAlarms.single()
        assertEquals(
            second.id,
            shadowOf(remaining.operation).savedIntent
                .getStringExtra(AlarmReceiver.EXTRA_ALARM_ID),
        )
    }

    @Test
    fun `reschedule all schedules only enabled alarms with a future occurrence`() = runBlocking {
        val repeating = alarm(
            id = "6cd1dce2-1bb0-469f-aed4-0b2cb90efac3",
            time = LocalTime.of(7, 30),
            repeatDays = setOf(DayOfWeek.FRIDAY),
        )
        val futureOneShot = alarm(
            id = "a4f9cd01-97cf-4667-8e7a-a88af7d764cb",
            time = LocalTime.of(9, 0),
            repeatDays = emptySet(),
        )
        val disabled = alarm(
            id = "1f343afa-0111-42d2-9576-5e05c819dfdf",
            time = LocalTime.of(10, 0),
            repeatDays = setOf(DayOfWeek.FRIDAY),
            enabled = false,
        )
        val expiredOneShot = alarm(
            id = "20747713-50f4-4c57-b21a-327490f1dd68",
            time = LocalTime.of(7, 0),
            repeatDays = emptySet(),
        )
        val repository = FakeAlarmRepository(
            listOf(repeating, futureOneShot, disabled, expiredOneShot),
        )
        val scheduler = AndroidAlarmScheduler(
            context = context,
            alarmManager = alarmManager,
            repository = repository,
            clock = Clock.fixed(now, zone),
            zoneProvider = { zone },
        )

        scheduler.rescheduleAll()

        val scheduled = shadowOf(alarmManager).scheduledAlarms
        assertEquals(2, scheduled.size)
        assertEquals(
            setOf(repeating.id, futureOneShot.id),
            scheduled.map {
                shadowOf(it.operation).savedIntent.getStringExtra(AlarmReceiver.EXTRA_ALARM_ID)
            }.toSet(),
        )
        assertEquals(
            setOf(
                Instant.parse("2026-07-30T23:30:00Z").toEpochMilli(),
                Instant.parse("2026-07-24T01:00:00Z").toEpochMilli(),
            ),
            scheduled.map { it.triggerAtMs }.toSet(),
        )
        assertEquals(false, repository.alarms.single { it.id == expiredOneShot.id }.enabled)
        assertEquals(
            expiredOneShot.id to AlarmEventResult.MISSED,
            repository.events.single().let { it.alarmId to it.result },
        )

        scheduler.rescheduleAll()
        assertEquals(1, repository.events.size)
    }

    @Test
    fun `schedule reports when exact alarm access is required`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val alarm = alarm()
        val scheduler = scheduler(listOf(alarm))

        assertThrows(ExactAlarmPermissionRequiredException::class.java) {
            scheduler.schedule(alarm, now.plusSeconds(600))
        }
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
        assertEquals(SchedulingResult.FAILURE, scheduler.healthSnapshot().lastResult)
    }

    @Test
    fun `scheduler diagnostics survive process reconstruction and cancellation`() {
        val alarm = alarm(id = "durable-diagnostics")
        val trigger = now.plusSeconds(600)
        scheduler(listOf(alarm)).schedule(alarm, trigger)

        val reconstructed = scheduler(listOf(alarm))
        assertEquals(
            SchedulerHealthSnapshot(SchedulingResult.SUCCESS, trigger),
            reconstructed.healthSnapshot(),
        )

        reconstructed.cancel(alarm.id)
        assertEquals(
            SchedulerHealthSnapshot(SchedulingResult.SUCCESS, null),
            scheduler(listOf(alarm)).healthSnapshot(),
        )
    }

    @Test
    fun `one shot from a previous local date is missed instead of rolling forward`() =
        runBlocking {
            val nextDayNow = Instant.parse("2026-07-25T00:00:00Z")
            val oneShot = alarm(
                id = "previous-day",
                time = LocalTime.of(9, 0),
                repeatDays = emptySet(),
                updatedAt = Instant.parse("2026-07-24T00:00:00Z"),
            )
            val repository = FakeAlarmRepository(listOf(oneShot))
            val scheduler = AndroidAlarmScheduler(
                context = context,
                alarmManager = alarmManager,
                repository = repository,
                clock = Clock.fixed(nextDayNow, zone),
                zoneProvider = { zone },
            )

            scheduler.rescheduleAll()

            assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
            assertEquals(false, repository.alarms.single().enabled)
            assertEquals(
                Instant.parse("2026-07-24T01:00:00Z"),
                repository.events.single().scheduledAt,
            )
        }

    @Test
    fun `alarm receiver forwards the alarm id to the foreground ringing service`() {
        val alarmId = "1b6ed349-d7ac-4f32-a20d-09070f17fa49"

        AlarmReceiver().onReceive(
            context,
            Intent(context, AlarmReceiver::class.java)
                .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId),
        )

        val startedService = shadowOf(RuntimeEnvironment.getApplication()).nextStartedService
        assertEquals(AlarmReceiver.ACTION_START_RINGING, startedService.action)
        assertEquals(alarmId, startedService.getStringExtra(AlarmReceiver.EXTRA_ALARM_ID))
        assertEquals(
            "com.wakemove.android.ringing.RingingService",
            startedService.component?.className,
        )
    }

    private fun scheduler(alarms: List<Alarm>) = AndroidAlarmScheduler(
        context = context,
        alarmManager = alarmManager,
        repository = FakeAlarmRepository(alarms),
        clock = Clock.fixed(now, zone),
        zoneProvider = { zone },
    )

    private fun alarm(
        id: String = "0f7f173c-7bf1-4f20-80eb-4405764398a8",
        time: LocalTime = LocalTime.of(7, 30),
        repeatDays: Set<DayOfWeek> = setOf(DayOfWeek.FRIDAY),
        enabled: Boolean = true,
        updatedAt: Instant = Instant.parse("2026-07-24T00:00:00Z"),
    ) = Alarm(
        id = id,
        time = time,
        label = "Morning",
        enabled = enabled,
        repeatDays = repeatDays,
        soundId = "default",
        vibrationEnabled = true,
        challengeType = ChallengeType.SQUAT,
        targetCount = 10,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = updatedAt,
    )
}

private class FakeAlarmRepository(
    alarms: List<Alarm>,
) : AlarmRepository {
    val alarms = alarms.toMutableList()
    val events = mutableListOf<AlarmEvent>()
    override fun observeAlarms(): Flow<List<Alarm>> = flowOf(alarms)

    override suspend fun upsertAlarm(alarm: Alarm) = error("not used")

    override suspend fun deleteAlarm(id: String) = error("not used")

    override suspend fun getAlarm(id: String): Alarm? = error("not used")

    override suspend fun saveSession(session: RingingSession) = error("not used")

    override suspend fun activeSession(): RingingSession? = null

    override suspend fun transitionSession(
        session: RingingSession,
        expectedStatuses: Set<com.wakemove.android.domain.SessionStatus>,
        event: AlarmEvent?,
        alarmUpdate: Alarm?,
    ): Boolean = error("not used")

    override suspend fun expireOneShot(
        alarm: Alarm,
        event: AlarmEvent,
        expectedUpdatedAt: Instant,
    ): Boolean {
        val index = alarms.indexOfFirst { it.id == alarm.id && it.enabled }
        if (index < 0) return false
        alarms[index] = alarm.copy(enabled = false, updatedAt = checkNotNull(event.finishedAt))
        events += event
        return true
    }

    override suspend fun appendEvent(event: AlarmEvent) = error("not used")

    override suspend fun recentEvents(limit: Int): List<AlarmEvent> = error("not used")

    override suspend fun clearHistory() = error("not used")
}
