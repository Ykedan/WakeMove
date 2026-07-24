package com.wakemove.android.ringing

import android.app.Application
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Looper
import com.wakemove.android.domain.Alarm
import com.wakemove.android.domain.AlarmEvent
import com.wakemove.android.domain.AlarmRepository
import com.wakemove.android.domain.ChallengeType
import com.wakemove.android.domain.RingingSession
import com.wakemove.android.domain.SessionStatus
import com.wakemove.android.scheduling.AlarmReceiver
import com.wakemove.android.scheduling.AlarmScheduler
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager

@RunWith(RobolectricTestRunner::class)
@Config(application = RingingServiceTestApplication::class, sdk = [35])
class RingingServiceTest {
    private lateinit var application: RingingServiceTestApplication

    @Before
    fun setUp() {
        application = org.robolectric.RuntimeEnvironment.getApplication()
            as RingingServiceTestApplication
        application.reset()
        ShadowPowerManager.clearWakeLocks()
    }

    @Test
    fun `start command immediately enters foreground and acquires wake lock`() {
        val serviceController = Robolectric.buildService(RingingService::class.java).create()
        val service = serviceController.get()

        val result = service.onStartCommand(startIntent(), 0, 1)

        val shadowService = shadowOf(service)
        val notification = shadowService.lastForegroundNotification
        val channel = service.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(RingingService.NOTIFICATION_CHANNEL_ID)
        assertEquals(Service.START_REDELIVER_INTENT, result)
        assertNotNull(notification)
        assertEquals(android.app.Notification.CATEGORY_ALARM, notification.category)
        assertNotNull(notification.fullScreenIntent)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld)
        assertEquals(SessionStatus.RINGING, application.repository.session?.status)
        assertEquals(listOf("persist", "play", "vibrate"), application.order)
    }

    @Test
    fun `snooze releases the foreground service wake lock`() = runBlocking {
        val service = startedService()

        assertTrue(application.ringingSessionController.snooze())
        shadowOf(Looper.getMainLooper()).idle()

        assertTerminalServiceStopped(service)
    }

    @Test
    fun `completion releases the foreground service wake lock`() = runBlocking {
        val service = startedService()

        assertTrue(application.ringingSessionController.complete())
        shadowOf(Looper.getMainLooper()).idle()

        assertTerminalServiceStopped(service)
    }

    @Test
    fun `bypass releases the foreground service wake lock`() = runBlocking {
        val service = startedService()

        assertTrue(application.ringingSessionController.bypass())
        shadowOf(Looper.getMainLooper()).idle()

        assertTerminalServiceStopped(service)
    }

    private fun startedService(): RingingService {
        val service = Robolectric.buildService(RingingService::class.java).create().get()
        service.onStartCommand(startIntent(), 0, 1)
        assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld)
        return service
    }

    private fun assertTerminalServiceStopped(service: RingingService) {
        assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld)
        assertTrue(shadowOf(service).isForegroundStopped)
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    private fun startIntent() = Intent(application, RingingService::class.java)
        .setAction(AlarmReceiver.ACTION_START_RINGING)
        .putExtra(AlarmReceiver.EXTRA_ALARM_ID, TEST_ALARM.id)
}

class RingingServiceTestApplication : Application(), RingingDependencies {
    internal lateinit var repository: ServiceAlarmRepository
        private set
    lateinit var order: MutableList<String>
        private set
    override lateinit var ringingSessionController: RingingSessionController
        private set

    override fun onCreate() {
        super.onCreate()
        reset()
    }

    fun reset() {
        order = mutableListOf()
        repository = ServiceAlarmRepository(TEST_ALARM, order)
        ringingSessionController = RingingSessionController(
            repository = repository,
            audioPlayer = ServiceAudioPlayer(order),
            vibrator = ServiceVibrator(order),
            scheduler = ServiceAlarmScheduler(),
            clock = Clock.fixed(TEST_NOW, ZoneOffset.UTC),
            zoneProvider = { ZoneOffset.UTC },
            sessionIdFactory = { "session-id" },
        )
    }
}

internal class ServiceAlarmRepository(
    private val alarm: Alarm,
    private val order: MutableList<String>,
) : AlarmRepository {
    var session: RingingSession? = null
        private set

    override fun observeAlarms(): Flow<List<Alarm>> = flowOf(listOf(alarm))

    override suspend fun upsertAlarm(alarm: Alarm) = error("not used")

    override suspend fun deleteAlarm(id: String) = error("not used")

    override suspend fun getAlarm(id: String): Alarm? = alarm.takeIf { it.id == id }

    override suspend fun saveSession(session: RingingSession) {
        order += "persist"
        this.session = session
    }

    override suspend fun activeSession(): RingingSession? =
        session?.takeIf { it.status == SessionStatus.RINGING || it.status == SessionStatus.SNOOZED }

    override suspend fun transitionSession(
        session: RingingSession,
        expectedStatuses: Set<SessionStatus>,
        event: AlarmEvent?,
    ): Boolean {
        val current = this.session ?: return false
        if (current.status !in expectedStatuses) return false
        this.session = session
        return true
    }

    override suspend fun appendEvent(event: AlarmEvent) = error("not used")

    override suspend fun recentEvents(limit: Int): List<AlarmEvent> = emptyList()

    override suspend fun clearHistory() = error("not used")
}

private class ServiceAudioPlayer(
    private val order: MutableList<String>,
) : AlarmAudioPlayer {
    override var soundState: AlarmSoundState = AlarmSoundState.STOPPED
        private set

    override fun play(soundId: String) {
        order += "play"
        soundState = AlarmSoundState.PLAYING
    }

    override fun stop() {
        soundState = AlarmSoundState.STOPPED
    }
}

private class ServiceVibrator(
    private val order: MutableList<String>,
) : AlarmVibrator {
    override fun start() {
        order += "vibrate"
    }

    override fun stop() = Unit
}

private class ServiceAlarmScheduler : AlarmScheduler {
    override fun schedule(alarm: Alarm, at: Instant) = Unit

    override fun cancel(alarmId: String) = Unit

    override suspend fun rescheduleAll() = Unit
}

private val TEST_NOW = Instant.parse("2026-07-24T23:30:00Z")

private val TEST_ALARM = Alarm(
    id = "alarm-id",
    time = LocalTime.of(7, 30),
    label = "Morning",
    enabled = true,
    repeatDays = setOf(DayOfWeek.FRIDAY),
    soundId = "default",
    vibrationEnabled = true,
    snoozeMinutes = 5,
    snoozeLimit = 3,
    challengeType = ChallengeType.SQUAT,
    targetCount = 10,
    createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
)
