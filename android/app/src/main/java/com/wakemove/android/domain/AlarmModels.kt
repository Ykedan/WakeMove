package com.wakemove.android.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

const val MAX_SNOOZE_COUNT = 3

enum class ChallengeType {
    SQUAT,
    JUMPING_JACK,
    HANDS_UP,
    VOICE_PHRASE,
}

enum class SessionStatus {
    RINGING,
    SNOOZED,
    COMPLETED,
    BYPASSED,
    MISSED,
}

enum class AlarmEventResult {
    COMPLETED,
    BYPASSED,
    MISSED,
}

data class Alarm(
    val id: String,
    val time: LocalTime,
    val label: String,
    val enabled: Boolean,
    val repeatDays: Set<DayOfWeek>,
    val soundId: String,
    val vibrationEnabled: Boolean,
    val snoozeMinutes: Int = 5,
    val snoozeLimit: Int = 3,
    val challengeType: ChallengeType,
    val targetCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class RingingSession(
    val id: String,
    val alarmId: String,
    val scheduledAt: Instant,
    val startedAt: Instant,
    val snoozeCount: Int,
    val challengeType: ChallengeType,
    val targetCount: Int,
    val status: SessionStatus,
    val pendingScheduleAt: Instant? = null,
)

data class PendingAlarmSchedule(
    val sessionId: String,
    val alarmId: String,
    val scheduledAt: Instant,
)

data class AlarmEvent(
    val id: String,
    val alarmId: String,
    val scheduledAt: Instant,
    val startedAt: Instant?,
    val finishedAt: Instant?,
    val challengeType: ChallengeType,
    val snoozeCount: Int,
    val result: AlarmEventResult,
)
