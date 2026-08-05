package com.wakemove.android.ui.alarms

import com.wakemove.android.i18n.tr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakemove.android.domain.Alarm
import com.wakemove.android.domain.AlarmRepository
import com.wakemove.android.domain.ChallengeType
import com.wakemove.android.domain.ScheduleCalculator
import com.wakemove.android.domain.VibrationIntensity
import com.wakemove.android.domain.VibrationPattern
import com.wakemove.android.health.HealthSnapshot
import com.wakemove.android.ringing.AlarmSoundCatalog
import com.wakemove.android.scheduling.AlarmScheduler
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AlarmEditorUiState(
    val draftId: String = "",
    val alarmId: String? = null,
    val hour: Int = 7,
    val minute: Int = 30,
    val label: String = "",
    val selectedDays: Set<DayOfWeek> = emptySet(),
    val soundId: String = AlarmSoundCatalog.DEFAULT_ID,
    val vibrationEnabled: Boolean = true,
    val vibrationPattern: VibrationPattern = VibrationPattern.GENTLE,
    val vibrationIntensity: VibrationIntensity = VibrationIntensity.MEDIUM,
    val challengeType: ChallengeType = ChallengeType.SQUAT,
    val targetCount: Int = 10,
    val health: HealthSnapshot,
    val validationInstant: Instant = Instant.EPOCH,
    val validationZone: ZoneId = ZoneId.systemDefault(),
) {
    val selectedTime: LocalTime
        get() = LocalTime.of(hour, minute)

    val healthMessage: String?
        get() = when {
            !health.canScheduleAlarms -> tr("请先完成健康检查")
            else -> null
        }

    val nextOccurrence: ZonedDateTime
        get() {
            val now = ZonedDateTime.ofInstant(validationInstant, validationZone)
            val previewAlarm = Alarm(
                id = alarmId ?: draftId,
                time = selectedTime,
                label = label,
                enabled = true,
                repeatDays = selectedDays,
                soundId = soundId,
                vibrationEnabled = vibrationEnabled,
                vibrationPattern = vibrationPattern,
                vibrationIntensity = vibrationIntensity,
                challengeType = challengeType,
                targetCount = targetCount,
                createdAt = validationInstant,
                updatedAt = validationInstant,
            )
            return checkNotNull(ScheduleCalculator.nextOccurrence(previewAlarm, now))
        }

    val nextOccurrenceLabel: String
        get() {
            val now = ZonedDateTime.ofInstant(validationInstant, validationZone)
            val next = nextOccurrence
            val dayLabel = when (next.toLocalDate()) {
                now.toLocalDate() -> tr("今天")
                now.toLocalDate().plusDays(1) -> tr("明天")
                else -> next.format(DateTimeFormatter.ofPattern(tr("M月d日")))
            }
            val minutes = Duration.between(now, next).toMinutes().coerceAtLeast(1)
            val relative = when {
                minutes < 60 -> tr("约 $minutes 分钟后")
                else -> tr("约 ${minutes / 60} 小时后")
            }
            return tr("下一次响铃：$dayLabel ${next.format(TIME_FORMAT)}（$relative）")
        }

    val canSave: Boolean
        get() = hour in 0..23 &&
            minute in 0..59 &&
            healthMessage == null &&
            (challengeType == ChallengeType.VOICE_PHRASE || targetCount > 0)

    companion object {
        fun fromAlarm(
            alarm: Alarm,
            health: HealthSnapshot,
            validationInstant: Instant = Instant.now(),
            validationZone: ZoneId = ZoneId.systemDefault(),
        ) = AlarmEditorUiState(
            draftId = alarm.id,
            alarmId = alarm.id,
            hour = alarm.time.hour,
            minute = alarm.time.minute,
            label = alarm.label,
            selectedDays = alarm.repeatDays,
            soundId = AlarmSoundCatalog.find(alarm.soundId).id,
            vibrationEnabled = alarm.vibrationEnabled,
            vibrationPattern = alarm.vibrationPattern,
            vibrationIntensity = alarm.vibrationIntensity,
            challengeType = alarm.challengeType,
            targetCount = alarm.targetCount,
            health = health,
            validationInstant = validationInstant,
            validationZone = validationZone,
        )
    }
}

data class AlarmOperationUiState(
    val isInFlight: Boolean = false,
    val errorMessage: String? = null,
)

class AlarmListViewModel(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val healthProvider: () -> HealthSnapshot,
    private val instantProvider: () -> Instant = Instant::now,
) : ViewModel() {
    private val mutationMutex = Mutex()
    private val _operationState = MutableStateFlow(AlarmOperationUiState())
    val operationState: StateFlow<AlarmOperationUiState> = _operationState.asStateFlow()
    val alarms: Flow<List<Alarm>> = repository.observeAlarms()
    val activeSession: Flow<com.wakemove.android.domain.RingingSession?> =
        repository.observeActiveSession()

    fun submitEnabledChange(alarm: Alarm, enabled: Boolean) {
        if (_operationState.value.isInFlight) return
        _operationState.value = AlarmOperationUiState(isInFlight = true)
        viewModelScope.launch {
            try {
                setEnabled(alarm, enabled)
                _operationState.value = AlarmOperationUiState()
            } catch (error: Exception) {
                _operationState.value = AlarmOperationUiState(
                    errorMessage = error.message ?: tr("操作失败，请重试"),
                )
            }
        }
    }

    suspend fun setEnabled(alarm: Alarm, enabled: Boolean) = mutationMutex.withLock {
        requireAlarmIsMutable(alarm.id)
        val previous = repository.getAlarm(alarm.id) ?: alarm
        if (enabled) {
            val currentHealth = healthProvider()
            if (!AlarmEditorUiState.fromAlarm(previous, currentHealth).canSave) {
                throw AlarmMutationException(tr("健康状态已变化，请重新检查"))
            }
        }
        val updated = previous.copy(
                enabled = enabled,
                updatedAt = instantProvider(),
        )
        persistAndReconcile(
            repository = repository,
            scheduler = scheduler,
            previous = previous,
            updated = updated,
            instantProvider = instantProvider,
        )
    }

    private suspend fun requireAlarmIsMutable(alarmId: String) {
        if (repository.activeSession()?.alarmId == alarmId) {
            throw AlarmMutationException(tr("闹钟正在响铃或贪睡中，完成挑战后才能修改"))
        }
    }
}

class AlarmEditorViewModel(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val healthProvider: () -> HealthSnapshot,
    private val instantProvider: () -> Instant = Instant::now,
    private val zoneProvider: () -> ZoneId = ZoneId::systemDefault,
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val saveMutex = Mutex()
    private val _operationState = MutableStateFlow(AlarmOperationUiState())
    val operationState: StateFlow<AlarmOperationUiState> = _operationState.asStateFlow()
    private var pendingCreateId: String? = null
    private var completedFingerprint: AlarmEditorUiState? = null
    private var completedAlarm: Alarm? = null

    fun newState(): AlarmEditorUiState {
        val now = instantProvider()
        val zone = zoneProvider()
        val time = defaultTime(now, zone)
        return AlarmEditorUiState(
            draftId = idProvider(),
            hour = time.hour,
            minute = time.minute,
            health = healthProvider(),
            validationInstant = now,
            validationZone = zone,
        )
    }

    suspend fun stateFor(alarmId: String): AlarmEditorUiState? =
        repository.getAlarm(alarmId)?.let { alarm ->
            AlarmEditorUiState.fromAlarm(
                alarm = alarm,
                health = healthProvider(),
                validationInstant = instantProvider(),
                validationZone = zoneProvider(),
            )
        }

    fun submit(state: AlarmEditorUiState, onSuccess: (Alarm) -> Unit) {
        if (_operationState.value.isInFlight) return
        _operationState.value = AlarmOperationUiState(isInFlight = true)
        viewModelScope.launch {
            try {
                val saved = save(state)
                _operationState.value = AlarmOperationUiState()
                onSuccess(saved)
            } catch (error: Exception) {
                _operationState.value = AlarmOperationUiState(
                    errorMessage = error.message ?: tr("保存失败，请重试"),
                )
            }
        }
    }

    fun submitDelete(alarmId: String, onSuccess: () -> Unit) {
        if (_operationState.value.isInFlight) return
        _operationState.value = AlarmOperationUiState(isInFlight = true)
        viewModelScope.launch {
            try {
                delete(alarmId)
                _operationState.value = AlarmOperationUiState()
                onSuccess()
            } catch (error: Exception) {
                _operationState.value = AlarmOperationUiState(
                    errorMessage = error.message ?: tr("删除失败，请重试"),
                )
            }
        }
    }

    suspend fun save(state: AlarmEditorUiState): Alarm = saveMutex.withLock {
        if (completedFingerprint == state) {
            return@withLock checkNotNull(completedAlarm)
        }
        val currentState = state.copy(
            health = healthProvider(),
            validationInstant = instantProvider(),
            validationZone = zoneProvider(),
        )
        if (!currentState.canSave) {
            throw AlarmMutationException(
                currentState.healthMessage
                    ?: tr("闹钟设置无效"),
            )
        }
        val now = instantProvider()
        val existing = state.alarmId?.let { repository.getAlarm(it) }
        if (existing != null) requireAlarmIsMutable(existing.id)
        val alarm = Alarm(
            id = existing?.id ?: state.draftId.ifBlank {
                pendingCreateId ?: idProvider().also {
                    pendingCreateId = it
                }
            },
            time = state.selectedTime,
            label = state.label.trim(),
            enabled = existing?.enabled ?: true,
            repeatDays = state.selectedDays,
            soundId = AlarmSoundCatalog.find(state.soundId).id,
            vibrationEnabled = state.vibrationEnabled,
            vibrationPattern = state.vibrationPattern,
            vibrationIntensity = state.vibrationIntensity,
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
        persistAndReconcile(
            repository = repository,
            scheduler = scheduler,
            previous = existing,
            updated = alarm,
            instantProvider = instantProvider,
        )
        completedFingerprint = state
        completedAlarm = alarm
        return alarm
    }

    suspend fun delete(alarmId: String) {
        requireAlarmIsMutable(alarmId)
        scheduler.cancel(alarmId)
        repository.deleteAlarm(alarmId)
        scheduler.rescheduleAll()
    }

    companion object {
        private const val DEFAULT_SNOOZE_MINUTES = 5
        private const val DEFAULT_SNOOZE_LIMIT = 3
    }

    private fun defaultTime(now: Instant, zone: ZoneId): LocalTime {
        val localNow = ZonedDateTime.ofInstant(now, zone)
        val minutesFromMidnight = localNow.hour * 60 + localNow.minute
        val rounded = ((minutesFromMidnight / 5) + 1) * 5
        return LocalTime.of((rounded / 60) % 24, rounded % 60)
    }

    private suspend fun requireAlarmIsMutable(alarmId: String) {
        val active = repository.activeSession()
        if (active?.alarmId == alarmId &&
            active.status in setOf(
                com.wakemove.android.domain.SessionStatus.RINGING,
                com.wakemove.android.domain.SessionStatus.SNOOZED,
            )
        ) {
            throw AlarmMutationException(tr("闹钟正在响铃或贪睡中，完成挑战后才能修改"))
        }
    }
}

class AlarmMutationException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

private suspend fun persistAndReconcile(
    repository: AlarmRepository,
    scheduler: AlarmScheduler,
    previous: Alarm?,
    updated: Alarm,
    instantProvider: () -> Instant,
) {
    repository.upsertAlarm(updated)
    try {
        scheduler.rescheduleAll()
    } catch (schedulingError: Exception) {
        if (previous == null) {
            repository.deleteAlarm(updated.id)
            scheduler.cancel(updated.id)
        } else {
            repository.upsertAlarm(previous)
        }
        try {
            scheduler.rescheduleAll()
        } catch (reconciliationError: Exception) {
            val visible = previous ?: repository.getAlarm(updated.id)
            if (visible?.enabled == true) {
                repository.upsertAlarm(
                    visible.copy(
                        enabled = false,
                        updatedAt = instantProvider(),
                    ),
                )
            }
            scheduler.cancel(updated.id)
            schedulingError.addSuppressed(reconciliationError)
        }
        throw AlarmMutationException(
            tr("保存失败，闹钟状态已恢复"),
            schedulingError,
        )
    }
}

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
