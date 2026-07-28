package com.wakemove.android.ui.alarms

import com.wakemove.android.domain.Alarm
import com.wakemove.android.domain.ScheduleCalculator
import java.time.ZonedDateTime

internal data class NextAlarmUiModel(
    val alarm: Alarm,
    val occurrence: ZonedDateTime,
)

internal fun findNextEnabledAlarm(
    alarms: List<Alarm>,
    now: ZonedDateTime,
): NextAlarmUiModel? = alarms
    .asSequence()
    .filter(Alarm::enabled)
    .mapNotNull { alarm ->
        ScheduleCalculator.nextOccurrence(alarm, now)
            ?.let { occurrence -> NextAlarmUiModel(alarm, occurrence) }
    }
    .minWithOrNull(
        compareBy<NextAlarmUiModel> { it.occurrence }
            .thenBy { it.alarm.id },
    )
