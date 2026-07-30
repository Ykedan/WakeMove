package com.wakemove.android.ui.alarms

import com.wakemove.android.domain.Alarm
import com.wakemove.android.domain.AlarmEvent
import com.wakemove.android.domain.AlarmRepository
import com.wakemove.android.domain.ChallengeType
import com.wakemove.android.domain.RingingSession
import com.wakemove.android.domain.SessionStatus
import com.wakemove.android.health.HealthSnapshot
import com.wakemove.android.health.HealthStatus
import com.wakemove.android.scheduling.AlarmScheduler
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmEditorReliabilityTest {
    private val now = Instant.parse("2026-07-27T00:00:00Z")
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `camera and microphone permission do not block saving a new alarm`() {
        val health = readyHealth.copy(
            camera = HealthStatus.ACTION_REQUIRED,
            microphone = HealthStatus.ACTION_REQUIRED,
        )
        val state = AlarmEditorUiState(
            draftId = "draft",
            hour = 9,
            minute = 0,
            challengeType = ChallengeType.SQUAT,
            targetCount = 5,
            health = health,
            validationInstant = now,
            validationZone = zone,
        )

        assertTrue(state.canSave)
    }

    @Test
    fun `past one shot time is saved for tomorrow`() {
        val repository = RecordingRepository()
        val scheduler = RecordingScheduler()
        val viewModel = AlarmEditorViewModel(
            repository = repository,
            scheduler = scheduler,
            healthProvider = { readyHealth },
            instantProvider = { now },
            zoneProvider = { zone },
            idProvider = { "alarm" },
        )
        val state = AlarmEditorUiState(
            draftId = "alarm",
            hour = 7,
            minute = 0,
            selectedDays = emptySet(),
            challengeType = ChallengeType.SQUAT,
            targetCount = 5,
            health = readyHealth,
            validationInstant = now,
            validationZone = zone,
        )

        val saved = runBlocking { viewModel.save(state) }

        assertEquals(java.time.LocalTime.of(7, 0), saved.time)
        assertEquals(1, repository.upserts)
        assertEquals(1, scheduler.reschedules)
    }

    @Test
    fun `ringing or snoozed alarm cannot be disabled or deleted`() {
        val repository = RecordingRepository().apply {
            active = RingingSession(
                id = "session",
                alarmId = "alarm",
                scheduledAt = now,
                startedAt = now,
                snoozeCount = 1,
                challengeType = ChallengeType.SQUAT,
                targetCount = 5,
                status = SessionStatus.SNOOZED,
                pendingScheduleAt = now.plusSeconds(300),
            )
        }
        val scheduler = RecordingScheduler()
        val alarm = Alarm(
            id = "alarm",
            time = java.time.LocalTime.of(7, 0),
            label = "",
            enabled = true,
            repeatDays = emptySet(),
            soundId = "default",
            vibrationEnabled = true,
            challengeType = ChallengeType.SQUAT,
            targetCount = 5,
            createdAt = now,
            updatedAt = now,
        )
        val list = AlarmListViewModel(repository, scheduler, { readyHealth })
        val editor = AlarmEditorViewModel(repository, scheduler, { readyHealth })

        assertThrows(AlarmMutationException::class.java) {
            runBlocking { list.setEnabled(alarm, false) }
        }
        assertThrows(AlarmMutationException::class.java) {
            runBlocking { editor.delete(alarm.id) }
        }
        assertEquals(0, scheduler.reschedules)
    }
}

private class RecordingRepository : AlarmRepository {
    var upserts = 0
    var active: RingingSession? = null
    override fun observeAlarms(): Flow<List<Alarm>> = flowOf(emptyList())
    override suspend fun upsertAlarm(alarm: Alarm) {
        upserts += 1
    }
    override suspend fun deleteAlarm(id: String) = Unit
    override suspend fun getAlarm(id: String): Alarm? = null
    override suspend fun saveSession(session: RingingSession) = Unit
    override suspend fun activeSession(): RingingSession? = active
    override suspend fun transitionSession(
        session: RingingSession,
        expectedStatuses: Set<SessionStatus>,
        event: AlarmEvent?,
        alarmUpdate: Alarm?,
    ): Boolean = false
    override suspend fun appendEvent(event: AlarmEvent) = Unit
    override suspend fun recentEvents(limit: Int): List<AlarmEvent> = emptyList()
    override suspend fun clearHistory() = Unit
}

private class RecordingScheduler : AlarmScheduler {
    var reschedules = 0
    override fun schedule(alarm: Alarm, at: Instant) = Unit
    override fun cancel(alarmId: String) = Unit
    override suspend fun rescheduleAll() {
        reschedules += 1
    }
}

private val readyHealth = HealthSnapshot(
    exactAlarm = HealthStatus.READY,
    notifications = HealthStatus.READY,
    fullScreenIntent = HealthStatus.READY,
    camera = HealthStatus.READY,
    microphone = HealthStatus.READY,
)
