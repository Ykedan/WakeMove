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
        ORDER BY time_nano_of_day ASC, created_at ASC, id ASC
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

    @Query(
        """
        SELECT * FROM ringing_sessions
        WHERE status IN ('RINGING', 'SNOOZED')
        ORDER BY started_at DESC, id DESC
        LIMIT 1
        """,
    )
    abstract suspend fun activeSession(): RingingSessionEntity?

    @Insert
    abstract suspend fun appendEvent(event: AlarmEventEntity)

    @Query(
        """
        SELECT * FROM alarm_events
        ORDER BY scheduled_at DESC, id DESC
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
