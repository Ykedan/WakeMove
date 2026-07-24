package com.wakemove.android.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.wakemove.android.MainActivity
import com.wakemove.android.domain.Alarm
import com.wakemove.android.domain.AlarmRepository
import com.wakemove.android.domain.ScheduleCalculator
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.flow.first

class AndroidAlarmScheduler(
    context: Context,
    private val alarmManager: AlarmManager,
    private val repository: AlarmRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : AlarmScheduler {
    private val appContext = context.applicationContext

    override fun schedule(alarm: Alarm, at: Instant) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()
        ) {
            throw ExactAlarmPermissionRequiredException()
        }

        val operation = checkNotNull(
            alarmPendingIntent(alarm.id, PendingIntent.FLAG_UPDATE_CURRENT),
        )
        val showIntent = PendingIntent.getActivity(
            appContext,
            requestCodeForAlarm(alarm.id),
            Intent(appContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmClock = AlarmManager.AlarmClockInfo(at.toEpochMilli(), showIntent)
        alarmManager.setAlarmClock(alarmClock, operation)
    }

    override fun cancel(alarmId: String) {
        alarmManager.cancel(
            alarmPendingIntent(alarmId, PendingIntent.FLAG_NO_CREATE) ?: return,
        )
    }

    override suspend fun rescheduleAll() {
        val now = ZonedDateTime.now(clock.withZone(zoneId))
        repository.observeAlarms().first().forEach { alarm ->
            val occurrence = alarm
                .takeIf(Alarm::enabled)
                ?.let { ScheduleCalculator.nextOccurrence(it, now) }

            if (occurrence == null) {
                cancel(alarm.id)
            } else {
                schedule(alarm, occurrence.toInstant())
            }
        }
    }

    private fun alarmPendingIntent(alarmId: String, lookupFlag: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            appContext,
            requestCodeForAlarm(alarmId),
            Intent(appContext, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_ALARM_FIRED)
                .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId),
            lookupFlag or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        internal fun requestCodeForAlarm(alarmId: String): Int =
            UUID.fromString(alarmId).hashCode()
    }
}
