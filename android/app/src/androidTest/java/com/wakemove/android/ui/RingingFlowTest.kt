package com.wakemove.android.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import com.wakemove.android.challenge.CameraGuidance
import com.wakemove.android.challenge.ChallengeProgress
import com.wakemove.android.challenge.SpeechChallengeState
import com.wakemove.android.domain.Alarm
import com.wakemove.android.domain.AlarmEvent
import com.wakemove.android.domain.AlarmEventResult
import com.wakemove.android.domain.AlarmRepository
import com.wakemove.android.domain.ChallengeType
import com.wakemove.android.domain.RingingSession
import com.wakemove.android.domain.SessionStatus
import com.wakemove.android.health.HealthSnapshot
import com.wakemove.android.health.HealthStatus
import com.wakemove.android.ringing.AlarmSoundState
import com.wakemove.android.ringing.AlarmAudioPlayer
import com.wakemove.android.ringing.AlarmVibrator
import com.wakemove.android.ringing.RingingSessionController
import com.wakemove.android.ringing.RingingUiState
import com.wakemove.android.scheduling.AlarmScheduler
import com.wakemove.android.ui.health.HealthIssue
import com.wakemove.android.ui.health.HealthScreen
import com.wakemove.android.ui.history.HistoryScreen
import com.wakemove.android.ui.onboarding.OnboardingScreen
import com.wakemove.android.ui.ringing.CameraChallengeScreen
import com.wakemove.android.ui.ringing.RingingScreen
import com.wakemove.android.ui.ringing.RingingFlowHost
import com.wakemove.android.ui.ringing.SpeechChallengeScreen
import com.wakemove.android.ui.theme.WakeMoveTheme
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RingingFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun ringingHasNoOrdinaryDismissAndHidesSnoozeAtThreeUses() {
        var challengeStarted = false
        setContent {
            RingingScreen(
                state = ringingState(snoozeCount = 3, remainingSnoozes = 0),
                sensorsUnavailable = false,
                onSnooze = {},
                onStartChallenge = { challengeStarted = true },
                onEmergencyBypass = {},
            )
        }

        composeRule.onAllNodesWithText("关闭闹钟").assertCountEquals(0)
        composeRule.onNodeWithTag("snooze_alarm").assertDoesNotExist()
        composeRule.onNodeWithText("07:30").assertIsDisplayed()
        composeRule.onNodeWithText("开始挑战").performClick()
        composeRule.runOnIdle { assertTrue(challengeStarted) }
    }

    @Test
    fun ringingFlowUsesTheRealSessionControllerForSnooze() {
        val repository = SessionRepository(alarm())
        val controller = RingingSessionController(
            repository = repository,
            audioPlayer = TestAudioPlayer(),
            vibrator = TestVibrator(),
            scheduler = NoOpScheduler,
        )
        runBlocking { assertTrue(controller.start("alarm")) }
        setContent {
            RingingFlowHost(
                controller = controller,
                healthProvider = { readyHealth },
            )
        }

        composeRule.onNodeWithTag("snooze_alarm").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            controller.state.value.session?.status == SessionStatus.SNOOZED
        }

        assertEquals(1, repository.session?.snoozeCount)
        assertEquals(SessionStatus.SNOOZED, repository.session?.status)
    }

    @Test
    fun cameraChallengeKeepsAlarmContextAndOffersFallbackWhenUnavailable() {
        var fallbackSelected = false
        setContent {
            CameraChallengeScreen(
                alarmTime = "07:30",
                alarmLabel = "晨练",
                challengeType = ChallengeType.SQUAT,
                progress = ChallengeProgress(
                    repetitions = 2,
                    targetCount = 5,
                    guidance = CameraGuidance.NO_PERSON,
                    fallbackAvailable = true,
                ),
                landmarks = listOf(0.25f to 0.25f, 0.75f to 0.75f),
                onUseSpeechFallback = { fallbackSelected = true },
            )
        }

        composeRule.onNodeWithText("07:30").assertIsDisplayed()
        composeRule.onNodeWithText("2 / 5").assertIsDisplayed()
        composeRule.onNodeWithTag("landmark_overlay").assertIsDisplayed()
        composeRule.onNodeWithText("请让全身进入画面").assertIsDisplayed()
        composeRule.onNodeWithText("改用语音挑战").performClick()
        composeRule.runOnIdle { assertTrue(fallbackSelected) }
    }

    @Test
    fun speechChallengeShowsListeningStateAndCameraFallbackOnFailure() {
        var fallbackSelected = false
        setContent {
            SpeechChallengeScreen(
                alarmTime = "07:30",
                alarmLabel = "起床",
                state = SpeechChallengeState.ServiceUnavailable("今天也要准时起床"),
                onRetry = {},
                onUseCameraFallback = { fallbackSelected = true },
            )
        }

        composeRule.onNodeWithText("今天也要准时起床").assertIsDisplayed()
        composeRule.onNodeWithText("语音服务不可用").assertIsDisplayed()
        composeRule.onNodeWithText("改用动作挑战").performClick()
        composeRule.runOnIdle { assertTrue(fallbackSelected) }
    }

    @Test
    fun emergencyBypassRequiresTenContinuousSecondsAndResetsAfterRelease() {
        var bypassed = false
        composeRule.mainClock.autoAdvance = false
        setContent {
            RingingScreen(
                state = ringingState(snoozeCount = 3, remainingSnoozes = 0),
                sensorsUnavailable = true,
                onSnooze = {},
                onStartChallenge = {},
                onEmergencyBypass = { bypassed = true },
            )
        }

        composeRule.onNodeWithTag("emergency_hold").performTouchInput {
            down(center)
        }
        composeRule.mainClock.advanceTimeBy(9_000)
        composeRule.onNodeWithTag("emergency_hold").performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.runOnIdle { assertFalse(bypassed) }

        composeRule.onNodeWithTag("emergency_hold").performTouchInput {
            down(center)
        }
        composeRule.mainClock.advanceTimeBy(10_100)
        composeRule.runOnIdle { assertTrue(bypassed) }
    }

    @Test
    fun historyShowsCompletedBypassedAndMissedLabels() {
        val base = event(AlarmEventResult.COMPLETED)
        setContent {
            HistoryScreen(
                events = listOf(
                    base,
                    base.copy(id = "2", result = AlarmEventResult.BYPASSED),
                    base.copy(id = "3", result = AlarmEventResult.MISSED),
                ),
                onClearHistory = {},
            )
        }

        composeRule.onNodeWithText("挑战完成").assertIsDisplayed()
        composeRule.onNodeWithText("紧急停止").assertIsDisplayed()
        composeRule.onNodeWithText("已错过").assertIsDisplayed()
    }

    @Test
    fun healthRepairActionUsesIssueIdentity() {
        var repaired: HealthIssue? = null
        setContent {
            HealthScreen(
                snapshot = HealthSnapshot(
                    exactAlarm = HealthStatus.ACTION_REQUIRED,
                    notifications = HealthStatus.READY,
                    fullScreenIntent = HealthStatus.READY,
                    camera = HealthStatus.READY,
                    microphone = HealthStatus.READY,
                ),
                onRepair = { repaired = it },
            )
        }
        composeRule.onNodeWithTag("repair_exact_alarm").performClick()
        composeRule.runOnIdle { assertEquals(HealthIssue.EXACT_ALARM, repaired) }
    }

    @Test
    fun onboardingCompletesFromPrimaryAction() {
        var completed = false
        setContent {
            OnboardingScreen(onComplete = { completed = true })
        }
        composeRule.onNodeWithText("开始使用").performClick()
        composeRule.runOnIdle { assertTrue(completed) }
    }

    private fun setContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            WakeMoveTheme(content)
        }
    }

    private fun ringingState(
        snoozeCount: Int,
        remainingSnoozes: Int,
    ): RingingUiState {
        val alarm = alarm()
        return RingingUiState(
            alarm = alarm,
            session = RingingSession(
                id = "session",
                alarmId = alarm.id,
                scheduledAt = Instant.parse("2026-07-27T00:00:00Z"),
                startedAt = Instant.parse("2026-07-27T00:00:00Z"),
                snoozeCount = snoozeCount,
                challengeType = alarm.challengeType,
                targetCount = alarm.targetCount,
                status = SessionStatus.RINGING,
            ),
            soundState = AlarmSoundState.PLAYING,
            remainingSnoozes = remainingSnoozes,
        )
    }

    private fun alarm() = Alarm(
            id = "alarm",
            time = LocalTime.of(7, 30),
            label = "晨练",
            enabled = true,
            repeatDays = emptySet(),
            soundId = "default",
            vibrationEnabled = true,
            challengeType = ChallengeType.SQUAT,
            targetCount = 5,
            createdAt = Instant.parse("2026-07-27T00:00:00Z"),
            updatedAt = Instant.parse("2026-07-27T00:00:00Z"),
        )

    private fun event(result: AlarmEventResult) = AlarmEvent(
        id = "1",
        alarmId = "alarm",
        scheduledAt = Instant.parse("2026-07-27T23:30:00Z"),
        startedAt = Instant.parse("2026-07-27T23:30:01Z"),
        finishedAt = Instant.parse("2026-07-27T23:31:00Z"),
        challengeType = ChallengeType.SQUAT,
        snoozeCount = 1,
        result = result,
    )

    private class SessionRepository(
        private val alarm: Alarm,
    ) : AlarmRepository {
        var session: RingingSession? = null
        private val events = mutableListOf<AlarmEvent>()

        override fun observeAlarms(): Flow<List<Alarm>> = flowOf(listOf(alarm))
        override suspend fun upsertAlarm(alarm: Alarm) = Unit
        override suspend fun deleteAlarm(id: String) = Unit
        override suspend fun getAlarm(id: String): Alarm? = alarm.takeIf { it.id == id }
        override suspend fun saveSession(session: RingingSession) {
            this.session = session
        }

        override suspend fun activeSession(): RingingSession? = session
        override suspend fun transitionSession(
            session: RingingSession,
            expectedStatuses: Set<SessionStatus>,
            event: AlarmEvent?,
            alarmUpdate: Alarm?,
        ): Boolean {
            val current = this.session ?: return false
            if (current.status !in expectedStatuses) return false
            this.session = session
            event?.let(events::add)
            return true
        }

        override suspend fun appendEvent(event: AlarmEvent) {
            events += event
        }

        override suspend fun recentEvents(limit: Int): List<AlarmEvent> = events.take(limit)
        override suspend fun clearHistory() {
            events.clear()
        }
    }

    private class TestAudioPlayer : AlarmAudioPlayer {
        override var soundState = AlarmSoundState.STOPPED
        override fun play(soundId: String) {
            soundState = AlarmSoundState.PLAYING
        }

        override fun stop() {
            soundState = AlarmSoundState.STOPPED
        }
    }

    private class TestVibrator : AlarmVibrator {
        override fun start() = Unit
        override fun stop() = Unit
    }

    private object NoOpScheduler : AlarmScheduler {
        override fun schedule(alarm: Alarm, at: Instant) = Unit
        override fun cancel(alarmId: String) = Unit
        override suspend fun rescheduleAll() = Unit
    }

    private companion object {
        val readyHealth = HealthSnapshot(
            exactAlarm = HealthStatus.READY,
            notifications = HealthStatus.READY,
            fullScreenIntent = HealthStatus.READY,
            camera = HealthStatus.READY,
            microphone = HealthStatus.READY,
        )
    }
}
