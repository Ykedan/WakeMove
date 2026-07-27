package com.wakemove.android.ui.alarms

import com.wakemove.android.domain.Alarm
import com.wakemove.android.domain.AlarmRepository
import com.wakemove.android.domain.ChallengeType
import com.wakemove.android.health.HealthSnapshot
import com.wakemove.android.health.HealthStatus
import com.wakemove.android.scheduling.AlarmScheduler
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlinx.coroutines.flow.Flow

data class AlarmEditorUiState(
    val alarmId: String? = null,
    val timeText: String = "",
    val label: String = "",
    val selectedDays: Set<DayOfWeek> = emptySet(),
    val challengeType: ChallengeType = ChallengeType.SQUAT,
    val targetCount: Int = 10,
    val health: HealthSnapshot,
) {
    val parsedTime: LocalTime?
        get() = try {
            LocalTime.parse(timeText)
        } catch (_: DateTimeParseException) {
            null
        }

    val healthMessage: String?
        get() = when {
            !health.canScheduleAlarms -> "请先完成健康检查"
            challengeType == ChallengeType.VOICE_PHRASE &&
                health.microphone != HealthStatus.READY ->
                "语音短语需要麦克风权限"
            challengeType != ChallengeType.VOICE_PHRASE &&
                health.camera != HealthStatus.READY ->
                "动作挑战需要相机权限"
            else -> null
        }

    val canSave: Boolean
        get() = parsedTime != null &&
            healthMessage == null &&
            (challengeType == ChallengeType.VOICE_PHRASE || targetCount > 0)

    companion object {
        fun fromAlarm(alarm: Alarm, health: HealthSnapshot) = AlarmEditorUiState(
            alarmId = alarm.id,
            timeText = alarm.time.toString(),
            label = alarm.label,
            selectedDays = alarm.repeatDays,
            challengeType = alarm.challengeType,
            targetCount = alarm.targetCount,
            health = health,
        )
    }
}

class AlarmListViewModel(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val instantProvider: () -> Instant = Instant::now,
) {
    val alarms: Flow<List<Alarm>> = repository.observeAlarms()

    suspend fun setEnabled(alarm: Alarm, enabled: Boolean) {
        repository.upsertAlarm(
            alarm.copy(
                enabled = enabled,
                updatedAt = instantProvider(),
            ),
        )
        scheduler.rescheduleAll()
    }
}

class AlarmEditorViewModel(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val healthProvider: () -> HealthSnapshot,
    private val instantProvider: () -> Instant = Instant::now,
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
) {
    fun newState(): AlarmEditorUiState = AlarmEditorUiState(
        health = healthProvider(),
    )

    suspend fun stateFor(alarmId: String): AlarmEditorUiState? =
        repository.getAlarm(alarmId)?.let { alarm ->
            AlarmEditorUiState.fromAlarm(alarm, healthProvider())
        }

    suspend fun save(state: AlarmEditorUiState): Alarm {
        require(state.canSave) { "Alarm editor state must be valid before saving" }
        val now = instantProvider()
        val existing = state.alarmId?.let { repository.getAlarm(it) }
        val alarm = Alarm(
            id = existing?.id ?: idProvider(),
            time = checkNotNull(state.parsedTime),
            label = state.label.trim(),
            enabled = existing?.enabled ?: true,
            repeatDays = state.selectedDays,
            soundId = existing?.soundId ?: DEFAULT_SOUND_ID,
            vibrationEnabled = existing?.vibrationEnabled ?: true,
            snoozeMinutes = existing?.snoozeMinutes ?: DEFAULT_SNOOZE_MINUTES,
            snoozeLimit = existing?.snoozeLimit ?: DEFAULT_SNOOZE_LIMIT,
            challengeType = state.challengeType,
            targetCount = if (state.challengeType == ChallengeType.VOICE_PHRASE) {
                1
            } else {
                state.targetCount
            },
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        repository.upsertAlarm(alarm)
        scheduler.rescheduleAll()
        return alarm
    }

    suspend fun delete(alarmId: String) {
        scheduler.cancel(alarmId)
        repository.deleteAlarm(alarmId)
        scheduler.rescheduleAll()
    }

    companion object {
        private const val DEFAULT_SOUND_ID = "default"
        private const val DEFAULT_SNOOZE_MINUTES = 5
        private const val DEFAULT_SNOOZE_LIMIT = 3
    }
}
