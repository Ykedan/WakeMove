package com.wakemove.android.domain

import java.time.ZonedDateTime

object ScheduleCalculator {
    fun nextOccurrence(alarm: Alarm, now: ZonedDateTime): ZonedDateTime? {
        if (alarm.repeatDays.isEmpty()) {
            val target = oneShotTarget(alarm, now.zone)
            return target.takeIf { it.isAfter(now) }
        }

        for (daysAhead in 0..7) {
            val date = now.toLocalDate().plusDays(daysAhead.toLong())
            val candidate = ZonedDateTime.of(date, alarm.time, now.zone)

            if (date.dayOfWeek in alarm.repeatDays && candidate.isAfter(now)) {
                return candidate
            }
        }

        return null
    }

    fun oneShotTarget(alarm: Alarm, zone: java.time.ZoneId): ZonedDateTime {
        require(alarm.repeatDays.isEmpty())
        val editedAt = alarm.updatedAt.atZone(zone)
        val targetDate = if (alarm.time.isAfter(editedAt.toLocalTime())) {
            editedAt.toLocalDate()
        } else {
            editedAt.toLocalDate().plusDays(1)
        }
        return ZonedDateTime.of(targetDate, alarm.time, zone)
    }
}
