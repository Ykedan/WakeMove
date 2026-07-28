package com.wakemove.android.ui.alarms

import com.wakemove.android.domain.Alarm
import com.wakemove.android.domain.ChallengeType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmListPresentationTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `returns null when no alarm is enabled`() {
        val result = findNextEnabledAlarm(
            alarms = listOf(alarm("off", 7, 0, enabled = false)),
            now = ZonedDateTime.of(2026, 7, 27, 6, 0, 0, 0, zone),
        )

        assertNull(result)
    }

    @Test
    fun `selects earliest real occurrence rather than earliest clock time`() {
        val now = ZonedDateTime.of(2026, 7, 27, 8, 0, 0, 0, zone)
        val laterToday = alarm("today", 9, 0, days = setOf(DayOfWeek.MONDAY))
        val earlierTomorrow = alarm("tomorrow", 7, 0, days = setOf(DayOfWeek.TUESDAY))

        val result = findNextEnabledAlarm(listOf(earlierTomorrow, laterToday), now)

        assertEquals("today", result?.alarm?.id)
        assertEquals(now.toLocalDate(), result?.occurrence?.toLocalDate())
    }

    @Test
    fun `one shot past its time is excluded`() {
        val now = ZonedDateTime.of(2026, 7, 27, 8, 0, 0, 0, zone)
        val result = findNextEnabledAlarm(
            listOf(alarm("expired", 7, 0, days = emptySet())),
            now,
        )

        assertNull(result)
    }

    @Test
    fun `selects lexicographically smaller id when occurrences are identical`() {
        val now = ZonedDateTime.of(2026, 7, 27, 6, 0, 0, 0, zone)
        val result = findNextEnabledAlarm(
            listOf(
                alarm("zebra", 7, 0, days = setOf(DayOfWeek.MONDAY)),
                alarm("alpha", 7, 0, days = setOf(DayOfWeek.MONDAY)),
            ),
            now,
        )

        assertEquals("alpha", result?.alarm?.id)
    }

    private fun alarm(
        id: String,
        hour: Int,
        minute: Int,
        enabled: Boolean = true,
        days: Set<DayOfWeek> = emptySet(),
    ) = Alarm(
        id = id,
        time = LocalTime.of(hour, minute),
        label = "Test alarm",
        enabled = enabled,
        repeatDays = days,
        soundId = "default",
        vibrationEnabled = true,
        challengeType = ChallengeType.SQUAT,
        targetCount = 10,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
}
