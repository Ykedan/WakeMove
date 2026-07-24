package com.wakemove.android.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleCalculatorTest {
    @Test
    fun one_shot_alarm_later_today_returns_its_time() {
        val alarm = alarmAt(7, 30)
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
    fun weekly_alarm_rolls_to_selected_day() {
        val alarm = alarmAt(7, 30, repeatDays = setOf(DayOfWeek.MONDAY))
        val now = ZonedDateTime.parse("2026-07-24T10:00:00+08:00[Asia/Shanghai]")

        assertEquals(
            ZonedDateTime.parse("2026-07-27T07:30:00+08:00[Asia/Shanghai]"),
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

    private fun alarmAt(
        hour: Int,
        minute: Int,
        repeatDays: Set<DayOfWeek> = emptySet(),
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
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
}
