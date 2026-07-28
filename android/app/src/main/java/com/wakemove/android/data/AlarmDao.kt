package com.wakemove.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AlarmDao {
    @Query(
        """
        SELECT * FROM alarms
        ORDER BY
            time_nano_of_day ASC,
            created_at_epoch_second ASC,
            created_at_nano ASC,
            id ASC
        """,
    )
    abstract fun observeAlarms(): Flow<List<AlarmEntity>>

    @Upsert
    abstract suspend fun upsertAlarm(alarm: AlarmEntity)

    @Query("DELETE FROM alarms WHERE id = :id")
    abstract suspend fun deleteAlarm(id: String)

    @Query("SELECT * FROM alarms WHERE id = :id")
    abstract suspend fun getAlarm(id: String): AlarmEntity?

    @Upsert
    abstract suspend fun saveSession(session: RingingSessionEntity)

    @Query("SELECT * FROM ringing_sessions WHERE id = :id")
    protected abstract suspend fun getSession(id: String): RingingSessionEntity?

    @Query(
        """
        SELECT * FROM ringing_sessions
        WHERE status IN ('RINGING', 'SNOOZED')
        ORDER BY started_at_epoch_second DESC, started_at_nano DESC, id DESC
        LIMIT 1
        """,
    )
    abstract suspend fun activeSession(): RingingSessionEntity?

    @Query(
        """
        SELECT * FROM ringing_sessions
        WHERE pending_schedule_at_epoch_second IS NOT NULL
        ORDER BY
            pending_schedule_at_epoch_second ASC,
            pending_schedule_at_nano ASC,
            id ASC
        """,
    )
    abstract suspend fun pendingSessions(): List<RingingSessionEntity>

    @Query(
        """
        UPDATE ringing_sessions
        SET
            pending_schedule_at_epoch_second = CASE WHEN status = 'SNOOZED'
                THEN pending_schedule_at_epoch_second ELSE NULL END,
            pending_schedule_at_nano = CASE WHEN status = 'SNOOZED'
                THEN pending_schedule_at_nano ELSE NULL END
        WHERE id = :sessionId
          AND pending_schedule_at_epoch_second = :scheduledAtEpochSecond
          AND pending_schedule_at_nano = :scheduledAtNano
        """,
    )
    abstract suspend fun acknowledgePendingSchedule(
        sessionId: String,
        scheduledAtEpochSecond: Long,
        scheduledAtNano: Int,
    ): Int

    @Insert
    abstract suspend fun appendEvent(event: AlarmEventEntity)

    @Transaction
    open suspend fun transitionSession(
        session: RingingSessionEntity,
        expectedStatuses: Set<String>,
        event: AlarmEventEntity?,
        alarmUpdate: AlarmEntity?,
    ): Boolean {
        val current = getSession(session.id) ?: return false
        if (current.status !in expectedStatuses) return false
        saveSession(session)
        if (event != null) appendEvent(event)
        if (alarmUpdate != null) upsertAlarm(alarmUpdate)
        return true
    }

    @Transaction
    open suspend fun replaceActiveSession(
        previous: RingingSessionEntity,
        expectedStatuses: Set<String>,
        previousEvent: AlarmEventEntity,
        previousAlarmUpdate: AlarmEntity?,
        next: RingingSessionEntity,
    ): Boolean {
        val current = getSession(previous.id) ?: return false
        if (current.status !in expectedStatuses) return false
        saveSession(previous)
        appendEvent(previousEvent)
        if (previousAlarmUpdate != null) upsertAlarm(previousAlarmUpdate)
        saveSession(next)
        return true
    }

    @Transaction
    open suspend fun expireOneShot(
        alarm: AlarmEntity,
        event: AlarmEventEntity,
        expectedUpdatedAtEpochSecond: Long,
        expectedUpdatedAtNano: Int,
    ): Boolean {
        val current = getAlarm(alarm.id) ?: return false
        if (!current.enabled || current.repeatDays != 0 ||
            current.updatedAtEpochSecond != expectedUpdatedAtEpochSecond ||
            current.updatedAtNano != expectedUpdatedAtNano
        ) return false
        upsertAlarm(alarm)
        appendEvent(event)
        return true
    }

    @Query(
        """
        SELECT * FROM alarm_events
        ORDER BY scheduled_at_epoch_second DESC, scheduled_at_nano DESC, id DESC
        LIMIT :limit
        """,
    )
    abstract suspend fun recentEvents(limit: Int): List<AlarmEventEntity>

    @Query("DELETE FROM alarm_events")
    protected abstract suspend fun clearEvents()

    @Query("DELETE FROM ringing_sessions")
    protected abstract suspend fun clearSessions()

    @Transaction
    open suspend fun clearHistory() {
        clearEvents()
        clearSessions()
    }
}
