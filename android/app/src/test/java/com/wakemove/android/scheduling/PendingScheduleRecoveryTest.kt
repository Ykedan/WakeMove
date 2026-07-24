package com.wakemove.android.scheduling

import com.wakemove.android.domain.Alarm
import com.wakemove.android.domain.AlarmEvent
import com.wakemove.android.domain.AlarmRepository
import com.wakemove.android.domain.ChallengeType
import com.wakemove.android.domain.PendingAlarmSchedule
import com.wakemove.android.domain.RingingSession
import com.wakemove.android.domain.SessionStatus
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingScheduleRecoveryTest {
    @Test
    fun `registers then acknowledges a pending schedule`() = runBlocking {
        val repository = RecoveryRepository(alarm())
        val pending = pending()
        repository.pending += pending
        val scheduler = RecoveryScheduler()

        val result = PendingScheduleRecovery(repository, scheduler).recover()

        assertEquals(listOf(repository.alarm to pending.scheduledAt), scheduler.scheduled)
        assertTrue(repository.pending.isEmpty())
        assertEquals(1, result.registeredCount)
        assertEquals(0, result.failureCount)
    }

    @Test
    fun `scheduler failure leaves pending state and does not block another alarm`() =
        runBlocking {
            val firstAlarm = alarm(id = "first")
            val secondAlarm = alarm(id = "second")
            val repository = RecoveryRepository(firstAlarm).apply {
                alarms[secondAlarm.id] = secondAlarm
                pending += pending(sessionId = "first-session", alarmId = firstAlarm.id)
                pending += pending(sessionId = "second-session", alarmId = secondAlarm.id)
            }
            val scheduler = RecoveryScheduler(failingAlarmId = firstAlarm.id)

            val result = PendingScheduleRecovery(repository, scheduler).recover()

            assertEquals(listOf(firstAlarm.id, secondAlarm.id), scheduler.attemptedAlarmIds)
            assertEquals(listOf(firstAlarm.id), repository.pending.map { it.alarmId })
            assertEquals(1, result.registeredCount)
            assertEquals(1, result.failureCount)
        }

    @Test
    fun `schedule before acknowledge crash window retries safely`() = runBlocking {
        val repository = RecoveryRepository(alarm()).apply {
            pending += pending()
            allowAcknowledge = false
        }
        val scheduler = RecoveryScheduler()
        val recovery = PendingScheduleRecovery(repository, scheduler)

        recovery.recover()
        assertEquals(1, repository.pending.size)

        repository.allowAcknowledge = true
        recovery.recover()

        assertEquals(2, scheduler.scheduled.size)
        assertTrue(repository.pending.isEmpty())
    }

    @Test
    fun `missing or disabled alarms are cancelled and acknowledged`() = runBlocking {
        val disabled = alarm(id = "disabled").copy(enabled = false)
        val repository = RecoveryRepository(disabled).apply {
            pending += pending(sessionId = "disabled-session", alarmId = disabled.id)
            pending += pending(sessionId = "missing-session", alarmId = "missing")
        }
        val scheduler = RecoveryScheduler()

        val result = PendingScheduleRecovery(repository, scheduler).recover()

        assertTrue(scheduler.scheduled.isEmpty())
        assertEquals(listOf(disabled.id, "missing"), scheduler.cancelled)
        assertTrue(repository.pending.isEmpty())
        assertEquals(2, result.discardedCount)
    }

    private fun pending(
        sessionId: String = "session",
        alarmId: String = "alarm",
    ) = PendingAlarmSchedule(
        sessionId = sessionId,
        alarmId = alarmId,
        scheduledAt = Instant.parse("2026-07-25T00:00:00.123456789Z"),
    )

    private fun alarm(id: String = "alarm") = Alarm(
        id = id,
        time = LocalTime.of(7, 30),
        label = "Morning",
        enabled = true,
        repeatDays = emptySet(),
        soundId = "default",
        vibrationEnabled = true,
        challengeType = ChallengeType.SQUAT,
        targetCount = 10,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
}

private class RecoveryRepository(
    val alarm: Alarm,
) : AlarmRepository {
    val alarms = mutableMapOf(alarm.id to alarm)
    val pending = mutableListOf<PendingAlarmSchedule>()
    var allowAcknowledge = true

    override fun observeAlarms(): Flow<List<Alarm>> = flowOf(alarms.values.toList())

    override suspend fun upsertAlarm(alarm: Alarm) = error("not used")

    override suspend fun deleteAlarm(id: String) = error("not used")

    override suspend fun getAlarm(id: String): Alarm? = alarms[id]

    override suspend fun saveSession(session: RingingSession) = error("not used")

    override suspend fun activeSession(): RingingSession? = error("not used")

    override suspend fun transitionSession(
        session: RingingSession,
        expectedStatuses: Set<SessionStatus>,
        event: AlarmEvent?,
        alarmUpdate: Alarm?,
    ): Boolean = error("not used")

    override suspend fun pendingSchedules(): List<PendingAlarmSchedule> = pending.toList()

    override suspend fun acknowledgePendingSchedule(
        sessionId: String,
        scheduledAt: Instant,
    ): Boolean {
        if (!allowAcknowledge) return false
        return pending.removeAll {
            it.sessionId == sessionId && it.scheduledAt == scheduledAt
        }
    }

    override suspend fun appendEvent(event: AlarmEvent) = error("not used")

    override suspend fun recentEvents(limit: Int): List<AlarmEvent> = error("not used")

    override suspend fun clearHistory() = error("not used")
}

private class RecoveryScheduler(
    private val failingAlarmId: String? = null,
) : AlarmScheduler {
    val attemptedAlarmIds = mutableListOf<String>()
    val scheduled = mutableListOf<Pair<Alarm, Instant>>()
    val cancelled = mutableListOf<String>()

    override fun schedule(alarm: Alarm, at: Instant) {
        attemptedAlarmIds += alarm.id
        if (alarm.id == failingAlarmId) error("scheduler unavailable")
        scheduled += alarm to at
    }

    override fun cancel(alarmId: String) {
        cancelled += alarmId
    }

    override suspend fun rescheduleAll() = error("not used")
}
