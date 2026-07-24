package com.wakemove.android.domain

import kotlinx.coroutines.flow.Flow

interface AlarmRepository {
    fun observeAlarms(): Flow<List<Alarm>>

    suspend fun upsertAlarm(alarm: Alarm)

    suspend fun deleteAlarm(id: String)

    suspend fun getAlarm(id: String): Alarm?

    suspend fun saveSession(session: RingingSession)

    suspend fun activeSession(): RingingSession?

    suspend fun appendEvent(event: AlarmEvent)

    suspend fun recentEvents(limit: Int = DEFAULT_EVENT_LIMIT): List<AlarmEvent>

    suspend fun clearHistory()

    companion object {
        const val DEFAULT_EVENT_LIMIT = 50
    }
}
