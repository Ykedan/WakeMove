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
import com.wakemove.android.domain.PendingAlarmSchedule
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
    fun `notification snooze action carries alarm and session identity`() {
        val service = startedService()
        val notification = shadowOf(service).lastForegroundNotification
        val sessionId = checkNotNull(application.repository.session).id

        val snoozeIntent = shadowOf(notification.actions.single().actionIntent).savedIntent

        assertEquals(RingingService.ACTION_SNOOZE, snoozeIntent.action)
        assertEquals(
            TEST_ALARM.id,
            snoozeIntent.getStringExtra(AlarmReceiver.EXTRA_ALARM_ID),
        )
        assertEquals(
            sessionId,
            snoozeIntent.getStringExtra(RingingService.EXTRA_SESSION_ID),
        )
        assertNotNull(snoozeIntent.data)
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

    @Test
    fun `stale snoozed state does not stop a later service start in the same process`() =
        runBlocking {
            val firstController = Robolectric.buildService(RingingService::class.java).create()
            val firstService = firstController.get()
            firstService.onStartCommand(startIntent(), 0, 1)
            assertTrue(application.ringingSessionController.snooze())
            shadowOf(Looper.getMainLooper()).idle()
            firstController.destroy()
            assertEquals(SessionStatus.SNOOZED, application.repository.session?.status)

            ShadowPowerManager.clearWakeLocks()
            val secondService = Robolectric.buildService(RingingService::class.java).create().get()
            secondService.onStartCommand(startIntent(), 0, 2)
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(SessionStatus.RINGING, application.repository.session?.status)
            assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld)
            assertFalse(shadowOf(secondService).isForegroundStopped)
            assertFalse(shadowOf(secondService).isStoppedBySelf)
        }

    @Test
    fun `duplicate start transfers terminal ownership to the newest start id`() = runBlocking {
        val service = startedService(startId = 1)
        service.onStartCommand(startIntent(), 0, 2)

        assertTrue(application.ringingSessionController.complete())
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(2, shadowOf(service).stopSelfResultId)
        assertTerminalServiceStopped(service)
    }

    @Test
    fun `service start recovers an unrelated pending schedule after hydration`() {
        val earlierAlarm = TEST_ALARM.copy(id = "earlier-alarm")
        val pending = PendingAlarmSchedule(
            sessionId = "earlier-session",
            alarmId = earlierAlarm.id,
            scheduledAt = TEST_NOW.plusSeconds(300),
        )
        application.repository.alarms[earlierAlarm.id] = earlierAlarm
        application.repository.extraPending += pending
        val service = Robolectric.buildService(RingingService::class.java).create().get()

        service.onStartCommand(startIntent(), 0, 2)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(earlierAlarm.id to pending.scheduledAt), application.scheduler.scheduled)
        assertTrue(application.repository.extraPending.isEmpty())
    }

    @Test
    fun `notification snooze restores the addressed session after process state loss`() =
        runBlocking {
            val first = Robolectric.buildService(RingingService::class.java).create()
            val firstService = first.get()
            firstService.onStartCommand(startIntent(), 0, 1)
            val sessionId = checkNotNull(application.repository.session).id
            first.destroy()
            application.recreateController()

            ShadowPowerManager.clearWakeLocks()
            val restoredService = Robolectric.buildService(RingingService::class.java).create().get()
            restoredService.onStartCommand(
                commandIntent(RingingService.ACTION_SNOOZE, TEST_ALARM.id, sessionId),
                0,
                2,
            )
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(SessionStatus.SNOOZED, application.repository.session?.status)
            assertTerminalServiceStopped(restoredService)
        }

    @Test
    fun `missing action stops foreground and releases alert resources`() {
        val service = Robolectric.buildService(RingingService::class.java).create().get()

        service.onStartCommand(Intent(application, RingingService::class.java), 0, 10)
        shadowOf(Looper.getMainLooper()).idle()

        assertTerminalServiceStopped(service)
        assertEquals(AlarmSoundState.STOPPED, application.audioPlayer.soundState)
        assertTrue(application.vibrator.stopCount > 0)
    }

    @Test
    fun `unknown action stops foreground and releases alert resources`() {
        val service = Robolectric.buildService(RingingService::class.java).create().get()

        service.onStartCommand(
            Intent(application, RingingService::class.java).setAction("unknown"),
            0,
            11,
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertTerminalServiceStopped(service)
    }

    @Test
    fun `missing alarm id rejects start and removes the notification`() {
        val service = Robolectric.buildService(RingingService::class.java).create().get()

        service.onStartCommand(
            Intent(application, RingingService::class.java)
                .setAction(AlarmReceiver.ACTION_START_RINGING),
            0,
            12,
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertTerminalServiceStopped(service)
    }

    @Test
    fun `deleted or disabled alarm rejects start and cleans up`() {
        listOf(null, TEST_ALARM.copy(enabled = false)).forEachIndexed { index, alarm ->
            application.reset()
            application.repository.alarms.clear()
            if (alarm != null) application.repository.alarms[alarm.id] = alarm
            ShadowPowerManager.clearWakeLocks()
            val service = Robolectric.buildService(RingingService::class.java).create().get()

            service.onStartCommand(startIntent(), 0, 20 + index)
            shadowOf(Looper.getMainLooper()).idle()

            assertTerminalServiceStopped(service)
        }
    }

    @Test
    fun `competing alarm start stops the current foreground session`() {
        val service = startedService(startId = 30)
        val competing = TEST_ALARM.copy(id = "competing")
        application.repository.alarms[competing.id] = competing

        service.onStartCommand(
            Intent(application, RingingService::class.java)
                .setAction(AlarmReceiver.ACTION_START_RINGING)
                .putExtra(AlarmReceiver.EXTRA_ALARM_ID, competing.id),
            0,
            31,
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertTerminalServiceStopped(service)
        assertEquals(AlarmSoundState.STOPPED, application.audioPlayer.soundState)
    }

    @Test
    fun `failed notification snooze stops foreground and removes notification`() {
        application.repository.alarms[TEST_ALARM.id] = TEST_ALARM.copy(snoozeLimit = 0)
        val service = startedService(startId = 40)
        val sessionId = checkNotNull(application.repository.session).id

        service.onStartCommand(
            commandIntent(RingingService.ACTION_SNOOZE, TEST_ALARM.id, sessionId),
            0,
            41,
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(SessionStatus.RINGING, application.repository.session?.status)
        assertTerminalServiceStopped(service)
        assertEquals(AlarmSoundState.STOPPED, application.audioPlayer.soundState)
    }

    private fun startedService(startId: Int = 1): RingingService {
        val service = Robolectric.buildService(RingingService::class.java).create().get()
        service.onStartCommand(startIntent(), 0, startId)
        assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld)
        return service
    }

    private fun assertTerminalServiceStopped(service: RingingService) {
        assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld)
        assertTrue(shadowOf(service).isForegroundStopped)
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertTrue(shadowOf(service).notificationShouldRemoved)
    }

    private fun startIntent() = Intent(application, RingingService::class.java)
        .setAction(AlarmReceiver.ACTION_START_RINGING)
        .putExtra(AlarmReceiver.EXTRA_ALARM_ID, TEST_ALARM.id)

    private fun commandIntent(action: String, alarmId: String, sessionId: String) =
        Intent(application, RingingService::class.java)
            .setAction(action)
            .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            .putExtra(RingingService.EXTRA_SESSION_ID, sessionId)
}

class RingingServiceTestApplication : Application(), RingingDependencies {
    internal lateinit var repository: ServiceAlarmRepository
        private set
    lateinit var order: MutableList<String>
        private set
    lateinit var audioPlayer: ServiceAudioPlayer
        private set
    lateinit var vibrator: ServiceVibrator
        private set
    lateinit var scheduler: ServiceAlarmScheduler
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
        audioPlayer = ServiceAudioPlayer(order)
        vibrator = ServiceVibrator(order)
        scheduler = ServiceAlarmScheduler()
        recreateController()
    }

    fun recreateController() {
        ringingSessionController = RingingSessionController(
            repository = repository,
            audioPlayer = audioPlayer,
            vibrator = vibrator,
            scheduler = scheduler,
            clock = Clock.fixed(TEST_NOW, ZoneOffset.UTC),
            zoneProvider = { ZoneOffset.UTC },
            sessionIdFactory = { "session-id" },
        )
    }
}

internal class ServiceAlarmRepository(
    alarm: Alarm,
    private val order: MutableList<String>,
) : AlarmRepository {
    val alarms = mutableMapOf(alarm.id to alarm)
    val extraPending = mutableListOf<PendingAlarmSchedule>()
    var session: RingingSession? = null
        private set

    override fun observeAlarms(): Flow<List<Alarm>> = flowOf(alarms.values.toList())

    override suspend fun upsertAlarm(alarm: Alarm) = error("not used")

    override suspend fun deleteAlarm(id: String) = error("not used")

    override suspend fun getAlarm(id: String): Alarm? = alarms[id]

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
        alarmUpdate: Alarm?,
    ): Boolean {
        val current = this.session ?: return false
        if (current.status !in expectedStatuses) return false
        this.session = session
        if (event != null) events += event
        if (alarmUpdate != null) alarms[alarmUpdate.id] = alarmUpdate
        return true
    }

    val events = mutableListOf<AlarmEvent>()

    override suspend fun pendingSchedules(): List<PendingAlarmSchedule> =
        listOfNotNull(
            session?.pendingScheduleAt?.let {
                PendingAlarmSchedule(
                    sessionId = checkNotNull(session).id,
                    alarmId = checkNotNull(session).alarmId,
                    scheduledAt = it,
                )
            },
        ) + extraPending

    override suspend fun acknowledgePendingSchedule(
        sessionId: String,
        scheduledAt: Instant,
    ): Boolean {
        val pending = extraPending.firstOrNull {
            it.sessionId == sessionId && it.scheduledAt == scheduledAt
        }
        if (pending != null) {
            extraPending.remove(pending)
            return true
        }
        val current = session ?: return false
        if (current.id != sessionId || current.pendingScheduleAt != scheduledAt) return false
        session = current.copy(pendingScheduleAt = null)
        return true
    }

    override suspend fun appendEvent(event: AlarmEvent) = error("not used")

    override suspend fun recentEvents(limit: Int): List<AlarmEvent> = emptyList()

    override suspend fun clearHistory() = error("not used")
}

class ServiceAudioPlayer(
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

class ServiceVibrator(
    private val order: MutableList<String>,
) : AlarmVibrator {
    var stopCount = 0
        private set
    override fun start() {
        order += "vibrate"
    }

    override fun stop() {
        stopCount += 1
    }
}

class ServiceAlarmScheduler : AlarmScheduler {
    val scheduled = mutableListOf<Pair<String, Instant>>()

    override fun schedule(alarm: Alarm, at: Instant) {
        scheduled += alarm.id to at
    }

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
