package com.wakemove.android.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "time_nano_of_day")
    val timeNanoOfDay: Long,
    val label: String,
    val enabled: Boolean,
    @ColumnInfo(name = "repeat_days")
    val repeatDays: Int,
    @ColumnInfo(name = "sound_id")
    val soundId: String,
    @ColumnInfo(name = "vibration_enabled")
    val vibrationEnabled: Boolean,
    @ColumnInfo(name = "snooze_minutes")
    val snoozeMinutes: Int,
    @ColumnInfo(name = "snooze_limit")
    val snoozeLimit: Int,
    @ColumnInfo(name = "challenge_type")
    val challengeType: String,
    @ColumnInfo(name = "target_count")
    val targetCount: Int,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

@Entity(
    tableName = "ringing_sessions",
    indices = [
        Index(value = ["alarm_id"]),
        Index(value = ["status", "started_at"]),
    ],
)
data class RingingSessionEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "alarm_id")
    val alarmId: String,
    @ColumnInfo(name = "scheduled_at")
    val scheduledAt: Long,
    @ColumnInfo(name = "started_at")
    val startedAt: Long,
    @ColumnInfo(name = "snooze_count")
    val snoozeCount: Int,
    @ColumnInfo(name = "challenge_type")
    val challengeType: String,
    @ColumnInfo(name = "target_count")
    val targetCount: Int,
    val status: String,
)

@Entity(
    tableName = "alarm_events",
    indices = [
        Index(value = ["alarm_id"]),
        Index(value = ["scheduled_at"]),
    ],
)
data class AlarmEventEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "alarm_id")
    val alarmId: String,
    @ColumnInfo(name = "scheduled_at")
    val scheduledAt: Long,
    @ColumnInfo(name = "started_at")
    val startedAt: Long?,
    @ColumnInfo(name = "finished_at")
    val finishedAt: Long?,
    @ColumnInfo(name = "challenge_type")
    val challengeType: String,
    @ColumnInfo(name = "snooze_count")
    val snoozeCount: Int,
    val result: String,
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey
    val key: String,
    val value: String,
)
