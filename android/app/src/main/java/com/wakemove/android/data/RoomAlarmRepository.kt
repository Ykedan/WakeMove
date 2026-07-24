package com.wakemove.android.data

import com.wakemove.android.domain.Alarm
import com.wakemove.android.domain.AlarmEvent
import com.wakemove.android.domain.AlarmEventResult
import com.wakemove.android.domain.AlarmRepository
import com.wakemove.android.domain.ChallengeType
import com.wakemove.android.domain.RingingSession
import com.wakemove.android.domain.SessionStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomAlarmRepository(
    private val dao: AlarmDao,
) : AlarmRepository {
    override fun observeAlarms(): Flow<List<Alarm>> =
        dao.observeAlarms().map { alarms -> alarms.map(AlarmEntity::toDomain) }

    override suspend fun upsertAlarm(alarm: Alarm) {
        dao.upsertAlarm(alarm.toEntity())
    }

    override suspend fun deleteAlarm(id: String) {
        dao.deleteAlarm(id)
    }

    override suspend fun getAlarm(id: String): Alarm? = dao.getAlarm(id)?.toDomain()

    override suspend fun saveSession(session: RingingSession) {
        dao.saveSession(session.toEntity())
    }

    override suspend fun activeSession(): RingingSession? = dao.activeSession()?.toDomain()

    override suspend fun appendEvent(event: AlarmEvent) {
        dao.appendEvent(event.toEntity())
    }

    override suspend fun recentEvents(limit: Int): List<AlarmEvent> {
        require(limit >= 0) { "limit must not be negative" }
        return dao.recentEvents(limit).map(AlarmEventEntity::toDomain)
    }

    override suspend fun clearHistory() {
        dao.clearHistory()
    }
}

private fun Alarm.toEntity() = AlarmEntity(
    id = id,
    timeNanoOfDay = time.toNanoOfDay(),
    label = label,
    enabled = enabled,
    repeatDays = repeatDays.toBitMask(),
    soundId = soundId,
    vibrationEnabled = vibrationEnabled,
    snoozeMinutes = snoozeMinutes,
    snoozeLimit = snoozeLimit,
    challengeType = challengeType.name,
    targetCount = targetCount,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

private fun AlarmEntity.toDomain() = Alarm(
    id = id,
    time = LocalTime.ofNanoOfDay(timeNanoOfDay),
    label = label,
    enabled = enabled,
    repeatDays = repeatDays.toDaysOfWeek(),
    soundId = soundId,
    vibrationEnabled = vibrationEnabled,
    snoozeMinutes = snoozeMinutes,
    snoozeLimit = snoozeLimit,
    challengeType = enumValueOf<ChallengeType>(challengeType),
    targetCount = targetCount,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

private fun RingingSession.toEntity() = RingingSessionEntity(
    id = id,
    alarmId = alarmId,
    scheduledAt = scheduledAt.toEpochMilli(),
    startedAt = startedAt.toEpochMilli(),
    snoozeCount = snoozeCount,
    challengeType = challengeType.name,
    targetCount = targetCount,
    status = status.name,
)

private fun RingingSessionEntity.toDomain() = RingingSession(
    id = id,
    alarmId = alarmId,
    scheduledAt = Instant.ofEpochMilli(scheduledAt),
    startedAt = Instant.ofEpochMilli(startedAt),
    snoozeCount = snoozeCount,
    challengeType = enumValueOf<ChallengeType>(challengeType),
    targetCount = targetCount,
    status = enumValueOf<SessionStatus>(status),
)

private fun AlarmEvent.toEntity() = AlarmEventEntity(
    id = id,
    alarmId = alarmId,
    scheduledAt = scheduledAt.toEpochMilli(),
    startedAt = startedAt?.toEpochMilli(),
    finishedAt = finishedAt?.toEpochMilli(),
    challengeType = challengeType.name,
    snoozeCount = snoozeCount,
    result = result.name,
)

private fun AlarmEventEntity.toDomain() = AlarmEvent(
    id = id,
    alarmId = alarmId,
    scheduledAt = Instant.ofEpochMilli(scheduledAt),
    startedAt = startedAt?.let(Instant::ofEpochMilli),
    finishedAt = finishedAt?.let(Instant::ofEpochMilli),
    challengeType = enumValueOf<ChallengeType>(challengeType),
    snoozeCount = snoozeCount,
    result = enumValueOf<AlarmEventResult>(result),
)

private fun Set<DayOfWeek>.toBitMask(): Int =
    fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

private fun Int.toDaysOfWeek(): Set<DayOfWeek> =
    DayOfWeek.entries.filterTo(linkedSetOf()) { day ->
        this and (1 shl (day.value - 1)) != 0
    }
