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
    @ColumnInfo(name = "created_at_epoch_second")
    val createdAtEpochSecond: Long,
    @ColumnInfo(name = "created_at_nano")
    val createdAtNano: Int,
    @ColumnInfo(name = "updated_at_epoch_second")
    val updatedAtEpochSecond: Long,
    @ColumnInfo(name = "updated_at_nano")
    val updatedAtNano: Int,
)

@Entity(
    tableName = "ringing_sessions",
    indices = [
        Index(value = ["alarm_id"]),
        Index(value = ["status", "started_at_epoch_second", "started_at_nano"]),
    ],
)
data class RingingSessionEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "alarm_id")
    val alarmId: String,
    @ColumnInfo(name = "scheduled_at_epoch_second")
    val scheduledAtEpochSecond: Long,
    @ColumnInfo(name = "scheduled_at_nano")
    val scheduledAtNano: Int,
    @ColumnInfo(name = "started_at_epoch_second")
    val startedAtEpochSecond: Long,
    @ColumnInfo(name = "started_at_nano")
    val startedAtNano: Int,
    @ColumnInfo(name = "snooze_count")
    val snoozeCount: Int,
    @ColumnInfo(name = "challenge_type")
    val challengeType: String,
    @ColumnInfo(name = "target_count")
    val targetCount: Int,
    val status: String,
    @ColumnInfo(name = "pending_schedule_at_epoch_second")
    val pendingScheduleAtEpochSecond: Long?,
    @ColumnInfo(name = "pending_schedule_at_nano")
    val pendingScheduleAtNano: Int?,
)

@Entity(
    tableName = "alarm_events",
    indices = [
        Index(value = ["alarm_id"]),
        Index(value = ["scheduled_at_epoch_second", "scheduled_at_nano"]),
    ],
)
data class AlarmEventEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "alarm_id")
    val alarmId: String,
    @ColumnInfo(name = "scheduled_at_epoch_second")
    val scheduledAtEpochSecond: Long,
    @ColumnInfo(name = "scheduled_at_nano")
    val scheduledAtNano: Int,
    @ColumnInfo(name = "started_at_epoch_second")
    val startedAtEpochSecond: Long?,
    @ColumnInfo(name = "started_at_nano")
    val startedAtNano: Int?,
    @ColumnInfo(name = "finished_at_epoch_second")
    val finishedAtEpochSecond: Long?,
    @ColumnInfo(name = "finished_at_nano")
    val finishedAtNano: Int?,
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
