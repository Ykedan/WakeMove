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
            timeText = "09:00",
            challengeType = ChallengeType.SQUAT,
            targetCount = 5,
            health = health,
            validationInstant = now,
            validationZone = zone,
        )

        assertTrue(state.canSave)
    }

    @Test
    fun `expired one shot is rejected before repository or scheduler mutation`() {
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
            timeText = "07:00",
            selectedDays = emptySet(),
            challengeType = ChallengeType.SQUAT,
            targetCount = 5,
            health = readyHealth,
            validationInstant = now,
            validationZone = zone,
        )

        val error = assertThrows(AlarmMutationException::class.java) {
            runBlocking { viewModel.save(state) }
        }

        assertTrue(error.message.orEmpty().contains("未来"))
        assertEquals(0, repository.upserts)
        assertEquals(0, scheduler.reschedules)
    }
}

private class RecordingRepository : AlarmRepository {
    var upserts = 0
    override fun observeAlarms(): Flow<List<Alarm>> = flowOf(emptyList())
    override suspend fun upsertAlarm(alarm: Alarm) {
        upserts += 1
    }
    override suspend fun deleteAlarm(id: String) = Unit
    override suspend fun getAlarm(id: String): Alarm? = null
    override suspend fun saveSession(session: RingingSession) = Unit
    override suspend fun activeSession(): RingingSession? = null
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
