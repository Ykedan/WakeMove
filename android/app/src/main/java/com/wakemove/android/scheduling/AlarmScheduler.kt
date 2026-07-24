package com.wakemove.android.scheduling

import com.wakemove.android.domain.Alarm
import java.time.Instant

interface AlarmScheduler {
    fun schedule(alarm: Alarm, at: Instant)

    fun cancel(alarmId: String)

    suspend fun rescheduleAll()
}

class ExactAlarmPermissionRequiredException :
    IllegalStateException("Exact alarm access is required to schedule WakeMove alarms")
