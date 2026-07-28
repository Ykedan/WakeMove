package com.wakemove.android.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleCalculatorTest {
    @Test
    fun one_shot_alarm_later_today_returns_its_time() {
        val alarm = alarmAt(
            hour = 7,
            minute = 30,
            updatedAt = Instant.parse("2026-07-23T22:00:00Z"),
        )
        val now = ZonedDateTime.parse("2026-07-24T07:00:00+08:00[Asia/Shanghai]")

        assertEquals(
            ZonedDateTime.parse("2026-07-24T07:30:00+08:00[Asia/Shanghai]"),
            ScheduleCalculator.nextOccurrence(alarm, now),
        )
    }

    @Test
    fun expired_one_shot_alarm_returns_null() {
        val alarm = alarmAt(7, 30)
        val now = ZonedDateTime.parse("2026-07-24T08:00:00+08:00[Asia/Shanghai]")

        assertEquals(null, ScheduleCalculator.nextOccurrence(alarm, now))
    }

    @Test
    fun one_shot_from_a_previous_local_date_is_not_eligible_today() {
        val alarm = alarmAt(
            hour = 9,
            minute = 0,
            updatedAt = Instant.parse("2026-07-24T00:00:00Z"),
        )
        val now = ZonedDateTime.parse("2026-07-25T08:00:00+08:00[Asia/Shanghai]")

        assertNull(ScheduleCalculator.nextOccurrence(alarm, now))
    }

    @Test
    fun one_shot_from_the_same_local_date_remains_eligible_later_today() {
        val alarm = alarmAt(
            hour = 9,
            minute = 0,
            updatedAt = Instant.parse("2026-07-25T00:00:00Z"),
        )
        val now = ZonedDateTime.parse("2026-07-25T08:00:00+08:00[Asia/Shanghai]")

        assertEquals(
            ZonedDateTime.parse("2026-07-25T09:00:00+08:00[Asia/Shanghai]"),
            ScheduleCalculator.nextOccurrence(alarm, now),
        )
    }

    @Test
    fun weekly_alarm_rolls_to_selected_day() {
        val alarm = alarmAt(7, 30, repeatDays = setOf(DayOfWeek.MONDAY))
        val now = ZonedDateTime.parse("2026-07-24T10:00:00+08:00[Asia/Shanghai]")

        assertEquals(
            ZonedDateTime.parse("2026-07-27T07:30:00+08:00[Asia/Shanghai]"),
            ScheduleCalculator.nextOccurrence(alarm, now),
        )
    }

    @Test
    fun weekly_alarm_after_todays_time_rolls_to_the_same_weekday_next_week() {
        val alarm = alarmAt(7, 30, repeatDays = setOf(DayOfWeek.FRIDAY))
        val now = ZonedDateTime.parse("2026-07-24T10:00:00+08:00[Asia/Shanghai]")

        assertEquals(
            ZonedDateTime.parse("2026-07-31T07:30:00+08:00[Asia/Shanghai]"),
            ScheduleCalculator.nextOccurrence(alarm, now),
        )
    }

    @Test
    fun weekly_alarm_at_the_current_time_rolls_to_the_next_week() {
        val alarm = alarmAt(7, 30, repeatDays = setOf(DayOfWeek.FRIDAY))
        val now = ZonedDateTime.parse("2026-07-24T07:30:00+08:00[Asia/Shanghai]")

        assertEquals(
            ZonedDateTime.parse("2026-07-31T07:30:00+08:00[Asia/Shanghai]"),
            ScheduleCalculator.nextOccurrence(alarm, now),
        )
    }

    @Test
    fun weekly_alarm_uses_zone_resolution_for_daylight_saving_gap() {
        val alarm = alarmAt(2, 30, repeatDays = setOf(DayOfWeek.SUNDAY))
        val now = ZonedDateTime.parse("2026-03-07T10:00:00-05:00[America/New_York]")

        assertEquals(
            ZonedDateTime.parse("2026-03-08T03:30:00-04:00[America/New_York]"),
            ScheduleCalculator.nextOccurrence(alarm, now),
        )
    }

    @Test
    fun alarm_event_records_a_terminal_event_result() {
        val event = AlarmEvent(
            id = "event-id",
            alarmId = "alarm-id",
            scheduledAt = Instant.parse("2026-01-01T07:30:00Z"),
            startedAt = Instant.parse("2026-01-01T07:30:01Z"),
            finishedAt = Instant.parse("2026-01-01T07:31:00Z"),
            challengeType = ChallengeType.SQUAT,
            snoozeCount = 0,
            result = AlarmEventResult.COMPLETED,
        )

        assertEquals(AlarmEventResult.COMPLETED, event.result)
    }

    private fun alarmAt(
        hour: Int,
        minute: Int,
        repeatDays: Set<DayOfWeek> = emptySet(),
        updatedAt: Instant = Instant.parse("2026-07-24T00:00:00Z"),
    ) = Alarm(
        id = "alarm-id",
        time = LocalTime.of(hour, minute),
        label = "Test alarm",
        enabled = true,
        repeatDays = repeatDays,
        soundId = "default",
        vibrationEnabled = true,
        snoozeMinutes = 5,
        snoozeLimit = 3,
        challengeType = ChallengeType.SQUAT,
        targetCount = 10,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = updatedAt,
    )
}
