package com.wakemove.android.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.wakemove.android.MainActivity
import com.wakemove.android.domain.Alarm
import com.wakemove.android.domain.AlarmEvent
import com.wakemove.android.domain.AlarmEventResult
import com.wakemove.android.domain.AlarmRepository
import com.wakemove.android.domain.ScheduleCalculator
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.first

class AndroidAlarmScheduler(
    context: Context,
    private val alarmManager: AlarmManager,
    private val repository: AlarmRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneProvider: () -> ZoneId = ZoneId::systemDefault,
) : AlarmScheduler {
    private val appContext = context.applicationContext
    private val diagnostics = appContext.getSharedPreferences(
        DIAGNOSTICS_PREFERENCES,
        Context.MODE_PRIVATE,
    )
    private val registeredAlarms = ConcurrentHashMap<String, Instant>()
    @Volatile
    private var lastResult = diagnostics.getString(KEY_LAST_RESULT, null)
        ?.let { runCatching { enumValueOf<SchedulingResult>(it) }.getOrNull() }
        ?: SchedulingResult.NEVER

    init {
        diagnostics.all.forEach { (key, value) ->
            if (key.startsWith(KEY_ALARM_PREFIX) && value is Long) {
                registeredAlarms[key.removePrefix(KEY_ALARM_PREFIX)] =
                    Instant.ofEpochMilli(value)
            }
        }
    }

    override fun schedule(alarm: Alarm, at: Instant) {
        try {
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
                Intent(appContext, MainActivity::class.java)
                    .setData(alarmUri(alarm.id)),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val alarmClock = AlarmManager.AlarmClockInfo(at.toEpochMilli(), showIntent)
            alarmManager.setAlarmClock(alarmClock, operation)
            registeredAlarms[alarm.id] = at
            lastResult = SchedulingResult.SUCCESS
            persistSchedule(alarm.id, at)
        } catch (error: RuntimeException) {
            registeredAlarms.remove(alarm.id)
            lastResult = SchedulingResult.FAILURE
            diagnostics.edit()
                .remove(KEY_ALARM_PREFIX + alarm.id)
                .putString(KEY_LAST_RESULT, lastResult.name)
                .apply()
            throw error
        }
    }

    override fun cancel(alarmId: String) {
        try {
            alarmPendingIntent(alarmId, PendingIntent.FLAG_NO_CREATE)?.let(alarmManager::cancel)
            registeredAlarms.remove(alarmId)
            lastResult = SchedulingResult.SUCCESS
            diagnostics.edit()
                .remove(KEY_ALARM_PREFIX + alarmId)
                .putString(KEY_LAST_RESULT, lastResult.name)
                .apply()
        } catch (error: RuntimeException) {
            lastResult = SchedulingResult.FAILURE
            diagnostics.edit()
                .putString(KEY_LAST_RESULT, lastResult.name)
                .apply()
            throw error
        }
    }

    override fun onAlarmDelivered(alarmId: String) {
        registeredAlarms.remove(alarmId)
        diagnostics.edit()
            .remove(KEY_ALARM_PREFIX + alarmId)
            .apply()
    }

    override suspend fun rescheduleAll() {
        val now = ZonedDateTime.ofInstant(clock.instant(), zoneProvider())
        val activeAlarmId = repository.activeSession()?.alarmId
        repository.observeAlarms().first().forEach { alarm ->
            if (alarm.id == activeAlarmId) return@forEach
            val occurrence = alarm
                .takeIf(Alarm::enabled)
                ?.let {
                    val intendedDate = it.updatedAt.atZone(now.zone).toLocalDate()
                    if (it.repeatDays.isEmpty() && intendedDate != now.toLocalDate()) {
                        null
                    } else {
                        ScheduleCalculator.nextOccurrence(it, now)
                    }
                }

            if (occurrence == null) {
                if (alarm.enabled && alarm.repeatDays.isEmpty()) {
                    val intendedDate = alarm.updatedAt.atZone(now.zone).toLocalDate()
                    val scheduledAt = ZonedDateTime.of(
                        intendedDate,
                        alarm.time,
                        now.zone,
                    ).toInstant()
                    val event = AlarmEvent(
                        id = "missed:${alarm.id}:${scheduledAt.epochSecond}:${scheduledAt.nano}",
                        alarmId = alarm.id,
                        scheduledAt = scheduledAt,
                        startedAt = null,
                        finishedAt = clock.instant(),
                        challengeType = alarm.challengeType,
                        snoozeCount = 0,
                        result = AlarmEventResult.MISSED,
                    )
                    repository.expireOneShot(
                        alarm = alarm.copy(
                            enabled = false,
                            updatedAt = clock.instant(),
                        ),
                        event = event,
                        expectedUpdatedAt = alarm.updatedAt,
                    )
                }
                cancel(alarm.id)
            } else {
                schedule(alarm, occurrence.toInstant())
            }
        }
    }

    override fun healthSnapshot() = SchedulerHealthSnapshot(
        lastResult = lastResult,
        nextRegisteredAt = registeredAlarms.values.minOrNull(),
    )

    private fun alarmPendingIntent(alarmId: String, lookupFlag: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            appContext,
            requestCodeForAlarm(alarmId),
            Intent(appContext, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_ALARM_FIRED)
                .setData(alarmUri(alarmId))
                .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId),
            lookupFlag or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun alarmUri(alarmId: String): Uri = Uri.Builder()
        .scheme("wakemove")
        .authority("alarm")
        .appendPath(alarmId)
        .build()

    private fun persistSchedule(alarmId: String, at: Instant) {
        diagnostics.edit()
            .putLong(KEY_ALARM_PREFIX + alarmId, at.toEpochMilli())
            .putString(KEY_LAST_RESULT, lastResult.name)
            .apply()
    }

    companion object {
        private const val DIAGNOSTICS_PREFERENCES = "wakemove_scheduler_diagnostics"
        private const val KEY_LAST_RESULT = "last_result"
        private const val KEY_ALARM_PREFIX = "alarm:"

        internal fun requestCodeForAlarm(alarmId: String): Int =
            alarmId.hashCode()
    }
}
