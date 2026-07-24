package com.wakemove.android.scheduling

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import com.wakemove.android.domain.Alarm
import com.wakemove.android.domain.AlarmEvent
import com.wakemove.android.domain.AlarmRepository
import com.wakemove.android.domain.ChallengeType
import com.wakemove.android.domain.RingingSession
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
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
        assertEquals(UUID.fromString(alarm.id).hashCode(), operation.requestCode)
        assertEquals(alarm.id, operation.savedIntent.getStringExtra(AlarmReceiver.EXTRA_ALARM_ID))
        assertEquals(
            AlarmReceiver::class.java.name,
            operation.savedIntent.component?.className,
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
        val scheduler = scheduler(listOf(repeating, futureOneShot, disabled, expiredOneShot))

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
        zoneId = zone,
    )

    private fun alarm(
        id: String = "0f7f173c-7bf1-4f20-80eb-4405764398a8",
        time: LocalTime = LocalTime.of(7, 30),
        repeatDays: Set<DayOfWeek> = setOf(DayOfWeek.FRIDAY),
        enabled: Boolean = true,
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
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
}

private class FakeAlarmRepository(
    private val alarms: List<Alarm>,
) : AlarmRepository {
    override fun observeAlarms(): Flow<List<Alarm>> = flowOf(alarms)

    override suspend fun upsertAlarm(alarm: Alarm) = error("not used")

    override suspend fun deleteAlarm(id: String) = error("not used")

    override suspend fun getAlarm(id: String): Alarm? = error("not used")

    override suspend fun saveSession(session: RingingSession) = error("not used")

    override suspend fun activeSession(): RingingSession? = error("not used")

    override suspend fun appendEvent(event: AlarmEvent) = error("not used")

    override suspend fun recentEvents(limit: Int): List<AlarmEvent> = error("not used")

    override suspend fun clearHistory() = error("not used")
}
