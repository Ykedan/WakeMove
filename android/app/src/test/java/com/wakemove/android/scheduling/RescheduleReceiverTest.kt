package com.wakemove.android.scheduling

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import com.wakemove.android.domain.Alarm
import com.wakemove.android.domain.AlarmEvent
import com.wakemove.android.domain.AlarmRepository
import com.wakemove.android.domain.ChallengeType
import com.wakemove.android.domain.RingingSession
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class RescheduleReceiverTest {
    @Before
    fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun `recovery runs asynchronously and finishes the pending broadcast`() {
        val scheduler = RecordingScheduler()
        val executor = QueuedExecutor()
        val receiver = RescheduleReceiver(
            schedulerProvider = { scheduler },
            executor = executor,
        )
        val receiverShadow = shadowOf(receiver)

        val context: Context = RuntimeEnvironment.getApplication()
        context.registerReceiver(
            receiver,
            IntentFilter(Intent.ACTION_BOOT_COMPLETED),
            Context.RECEIVER_EXPORTED,
        )
        context.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED))
        shadowOf(Looper.getMainLooper()).idle()

        val pendingResult = checkNotNull(receiverShadow.originalPendingResult)
        assertTrue(receiverShadow.wentAsync())
        assertFalse(scheduler.rescheduled)
        assertFalse(shadowOf(pendingResult).future.isDone)

        executor.runNext()

        assertTrue(scheduler.rescheduled)
        assertTrue(shadowOf(pendingResult).future.isDone)
    }

    @Test
    fun `recovery finishes the pending broadcast when rescheduling fails`() {
        val executor = QueuedExecutor()
        val receiver = RescheduleReceiver(
            schedulerProvider = { RecordingScheduler(fail = true) },
            executor = executor,
        )
        val receiverShadow = shadowOf(receiver)

        val context: Context = RuntimeEnvironment.getApplication()
        context.registerReceiver(
            receiver,
            IntentFilter(Intent.ACTION_TIME_CHANGED),
            Context.RECEIVER_EXPORTED,
        )
        context.sendBroadcast(Intent(Intent.ACTION_TIME_CHANGED))
        shadowOf(Looper.getMainLooper()).idle()
        val pendingResult = checkNotNull(receiverShadow.originalPendingResult)

        executor.runNext()

        assertTrue(shadowOf(pendingResult).future.isDone)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `timezone change broadcast recalculates alarm in the current system zone`() {
        val originalTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
            val context: Context = RuntimeEnvironment.getApplication()
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            val alarm = repeatingAlarm()
            val scheduler = AndroidAlarmScheduler(
                context = context,
                alarmManager = alarmManager,
                repository = SingleAlarmRepository(alarm),
                clock = Clock.fixed(
                    Instant.parse("2026-07-24T00:00:00Z"),
                    ZoneId.of("UTC"),
                ),
            )
            runBlocking {
                scheduler.rescheduleAll()
            }
            assertEquals(
                Instant.parse("2026-07-30T23:30:00Z").toEpochMilli(),
                shadowOf(alarmManager).scheduledAlarms.single().triggerAtMs,
            )

            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            val executor = QueuedExecutor()
            val receiver = RescheduleReceiver(
                schedulerProvider = { scheduler },
                executor = executor,
            )
            context.registerReceiver(
                receiver,
                IntentFilter(Intent.ACTION_TIMEZONE_CHANGED),
                Context.RECEIVER_EXPORTED,
            )

            context.sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED))
            shadowOf(Looper.getMainLooper()).idle()
            executor.runNext()

            assertEquals(
                Instant.parse("2026-07-24T14:30:00Z").toEpochMilli(),
                shadowOf(alarmManager).scheduledAlarms.single().triggerAtMs,
            )
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    private fun repeatingAlarm() = Alarm(
        id = "ef86cb8d-2842-4aa0-b918-687c3aeecab4",
        time = LocalTime.of(7, 30),
        label = "Morning",
        enabled = true,
        repeatDays = setOf(DayOfWeek.FRIDAY),
        soundId = "default",
        vibrationEnabled = true,
        challengeType = ChallengeType.SQUAT,
        targetCount = 10,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
}

private class QueuedExecutor : Executor {
    private val tasks = ArrayDeque<Runnable>()

    override fun execute(command: Runnable) {
        tasks.addLast(command)
    }

    fun runNext() {
        tasks.removeFirst().run()
    }
}

private class RecordingScheduler(
    private val fail: Boolean = false,
) : AlarmScheduler {
    var rescheduled = false

    override fun schedule(alarm: Alarm, at: Instant) = Unit

    override fun cancel(alarmId: String) = Unit

    override suspend fun rescheduleAll() {
        if (fail) error("boom")
        rescheduled = true
    }
}

private class SingleAlarmRepository(
    private val alarm: Alarm,
) : AlarmRepository {
    override fun observeAlarms(): Flow<List<Alarm>> = flowOf(listOf(alarm))

    override suspend fun upsertAlarm(alarm: Alarm) = error("not used")

    override suspend fun deleteAlarm(id: String) = error("not used")

    override suspend fun getAlarm(id: String): Alarm? = error("not used")

    override suspend fun saveSession(session: RingingSession) = error("not used")

    override suspend fun activeSession(): RingingSession? = error("not used")

    override suspend fun transitionSession(
        session: RingingSession,
        expectedStatuses: Set<com.wakemove.android.domain.SessionStatus>,
        event: AlarmEvent?,
    ): Boolean = error("not used")

    override suspend fun appendEvent(event: AlarmEvent) = error("not used")

    override suspend fun recentEvents(limit: Int): List<AlarmEvent> = error("not used")

    override suspend fun clearHistory() = error("not used")
}
