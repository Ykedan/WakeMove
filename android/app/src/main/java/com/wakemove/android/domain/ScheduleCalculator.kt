package com.wakemove.android.domain

import java.time.ZonedDateTime

object ScheduleCalculator {
    fun nextOccurrence(alarm: Alarm, now: ZonedDateTime): ZonedDateTime? {
        if (alarm.repeatDays.isEmpty() &&
            alarm.updatedAt.atZone(now.zone).toLocalDate() != now.toLocalDate()
        ) {
            return null
        }

        for (daysAhead in 0..7) {
            val date = now.toLocalDate().plusDays(daysAhead.toLong())
            val candidate = ZonedDateTime.of(date, alarm.time, now.zone)

            if (alarm.repeatDays.isEmpty()) {
                return candidate.takeIf { it.isAfter(now) }
            }

            if (date.dayOfWeek in alarm.repeatDays && candidate.isAfter(now)) {
                return candidate
            }
        }

        return null
    }
}
