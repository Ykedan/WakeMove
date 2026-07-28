package com.wakemove.android.ui.alarms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakemove.android.domain.Alarm
import com.wakemove.android.domain.AlarmRepository
import com.wakemove.android.domain.ChallengeType
import com.wakemove.android.health.HealthSnapshot
import com.wakemove.android.scheduling.AlarmScheduler
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
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
    val timeText: String = "",
    val label: String = "",
    val selectedDays: Set<DayOfWeek> = emptySet(),
    val challengeType: ChallengeType = ChallengeType.SQUAT,
    val targetCount: Int = 10,
    val health: HealthSnapshot,
    val validationInstant: Instant = Instant.EPOCH,
    val validationZone: ZoneId = ZoneId.systemDefault(),
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
            else -> null
        }

    val scheduleMessage: String?
        get() {
            val time = parsedTime ?: return null
            if (selectedDays.isNotEmpty()) return null
            val now = ZonedDateTime.ofInstant(validationInstant, validationZone)
            val occurrence = ZonedDateTime.of(now.toLocalDate(), time, validationZone)
            return if (occurrence.isAfter(now)) null else "单次闹钟必须选择未来时间"
        }

    val canSave: Boolean
        get() = parsedTime != null &&
            healthMessage == null &&
            scheduleMessage == null &&
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
            timeText = alarm.time.toString(),
            label = alarm.label,
            selectedDays = alarm.repeatDays,
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

    fun submitEnabledChange(alarm: Alarm, enabled: Boolean) {
        if (_operationState.value.isInFlight) return
        _operationState.value = AlarmOperationUiState(isInFlight = true)
        viewModelScope.launch {
            try {
                setEnabled(alarm, enabled)
                _operationState.value = AlarmOperationUiState()
            } catch (error: Exception) {
                _operationState.value = AlarmOperationUiState(
                    errorMessage = error.message ?: "操作失败，请重试",
                )
            }
        }
    }

    suspend fun setEnabled(alarm: Alarm, enabled: Boolean) = mutationMutex.withLock {
        val previous = repository.getAlarm(alarm.id) ?: alarm
        if (enabled) {
            val currentHealth = healthProvider()
            if (!AlarmEditorUiState.fromAlarm(previous, currentHealth).canSave) {
                throw AlarmMutationException("健康状态已变化，请重新检查")
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

    fun newState(): AlarmEditorUiState = AlarmEditorUiState(
        draftId = idProvider(),
        health = healthProvider(),
        validationInstant = instantProvider(),
        validationZone = zoneProvider(),
    )

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
                    errorMessage = error.message ?: "保存失败，请重试",
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
                    errorMessage = error.message ?: "删除失败，请重试",
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
                    ?: currentState.scheduleMessage
                    ?: "闹钟设置无效",
            )
        }
        val now = instantProvider()
        val existing = state.alarmId?.let { repository.getAlarm(it) }
        val alarm = Alarm(
            id = existing?.id ?: state.draftId.ifBlank {
                pendingCreateId ?: idProvider().also {
                    pendingCreateId = it
                }
            },
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
            "保存失败，闹钟状态已恢复",
            schedulingError,
        )
    }
}
