package com.wakemove.android.ui

import android.Manifest
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.cancel
import androidx.compose.ui.test.up
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.espresso.Espresso
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wakemove.android.challenge.CameraGuidance
import com.wakemove.android.challenge.ChallengeProgress
import com.wakemove.android.challenge.SpeechChallengeState
import com.wakemove.android.challenge.SpeechChallengeController
import com.wakemove.android.challenge.SpeechRecognitionEvent
import com.wakemove.android.challenge.SpeechRecognitionRequest
import com.wakemove.android.challenge.SpeechRecognitionSource
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
import com.wakemove.android.scheduling.SchedulerHealthSnapshot
import com.wakemove.android.scheduling.SchedulingResult
import com.wakemove.android.ui.health.HealthIssue
import com.wakemove.android.ui.health.HealthScreen
import com.wakemove.android.ui.history.HistoryScreen
import com.wakemove.android.ui.onboarding.OnboardingScreen
import com.wakemove.android.ui.ringing.CameraChallengeScreen
import com.wakemove.android.ui.ringing.RingingScreen
import com.wakemove.android.ui.ringing.RingingFlowHost
import com.wakemove.android.ui.ringing.SpeechChallengeScreen
import com.wakemove.android.ui.ringing.SensorPermissionRequester
import com.wakemove.android.ui.theme.WakeMoveTheme
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
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
    fun grantedCameraPermissionRefreshesHealthAndEntersTheRealChallenge() {
        val repository = SessionRepository(alarm())
        val controller = sessionController(repository)
        runBlocking { controller.start("alarm") }
        var health by mutableStateOf(
            readyHealth.copy(camera = HealthStatus.ACTION_REQUIRED),
        )
        var requestedPermission: String? = null
        var deliverResult: ((Boolean) -> Unit)? = null
        setContent {
            RingingFlowHost(
                controller = controller,
                healthProvider = { health },
                permissionRequester = SensorPermissionRequester { permission, result ->
                    requestedPermission = permission
                    deliverResult = result
                },
            )
        }

        composeRule.onNodeWithTag("start_challenge").performClick()
        composeRule.onNodeWithText("相机只在动作挑战期间启用").assertIsDisplayed()
        composeRule.onNodeWithText("允许相机").performClick()
        composeRule.runOnIdle {
            assertEquals(Manifest.permission.CAMERA, requestedPermission)
            health = readyHealth
            checkNotNull(deliverResult).invoke(true)
        }

        composeRule.onNodeWithTag("landmark_overlay").assertIsDisplayed()
        composeRule.onNodeWithText("60 秒后可改用语音").assertIsDisplayed()
    }

    @Test
    fun permanentPermissionDenialOffersSystemRepairWithoutStoppingRinging() {
        val repository = SessionRepository(alarm())
        val controller = sessionController(repository)
        val lifecycleOwner = ControlledLifecycleOwner()
        var health by mutableStateOf(
            readyHealth.copy(camera = HealthStatus.ACTION_REQUIRED),
        )
        runBlocking { controller.start("alarm") }
        var repaired: HealthIssue? = null
        lifecycleOwner.handle(Lifecycle.Event.ON_CREATE)
        lifecycleOwner.handle(Lifecycle.Event.ON_START)
        lifecycleOwner.handle(Lifecycle.Event.ON_RESUME)
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                RingingFlowHost(
                    controller = controller,
                    healthProvider = { health },
                    permissionRequester = SensorPermissionRequester { _, result -> result(false) },
                    isPermissionPermanentlyDenied = { true },
                    onRepairHealth = {
                        repaired = it
                        health = readyHealth
                    },
                )
            }
        }

        composeRule.onNodeWithTag("start_challenge").performClick()
        composeRule.onNodeWithText("允许相机").performClick()
        composeRule.onNodeWithText("请在系统设置开启相机权限").assertIsDisplayed()
        composeRule.onNodeWithText("打开权限设置").performClick()

        composeRule.runOnIdle {
            assertEquals(HealthIssue.CAMERA, repaired)
            assertEquals(SessionStatus.RINGING, controller.state.value.session?.status)
            lifecycleOwner.handle(Lifecycle.Event.ON_PAUSE)
            lifecycleOwner.handle(Lifecycle.Event.ON_RESUME)
        }
        composeRule.onNodeWithTag("landmark_overlay").assertIsDisplayed()
    }

    @Test
    fun permissionRationaleRestoresAndSystemBackReturnsToRinging() {
        val repository = SessionRepository(alarm())
        val controller = sessionController(repository)
        runBlocking { controller.start("alarm") }
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            WakeMoveTheme {
                RingingFlowHost(
                    controller = controller,
                    healthProvider = {
                        readyHealth.copy(camera = HealthStatus.ACTION_REQUIRED)
                    },
                    permissionRequester = SensorPermissionRequester { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag("start_challenge").performClick()
        composeRule.onNodeWithText("相机只在动作挑战期间启用").assertIsDisplayed()
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText("相机只在动作挑战期间启用").assertIsDisplayed()
        Espresso.pressBack()
        composeRule.onNodeWithTag("start_challenge").assertIsDisplayed()
    }

    @Test
    fun temporaryPermissionDenialCanRetryTheRuntimeRequest() {
        val repository = SessionRepository(alarm())
        val controller = sessionController(repository)
        runBlocking { controller.start("alarm") }
        var requestCount = 0
        var health by mutableStateOf(
            readyHealth.copy(camera = HealthStatus.ACTION_REQUIRED),
        )
        setContent {
            RingingFlowHost(
                controller = controller,
                healthProvider = { health },
                permissionRequester = SensorPermissionRequester { _, result ->
                    requestCount += 1
                    if (requestCount > 1) health = readyHealth
                    result(requestCount > 1)
                },
                isPermissionPermanentlyDenied = { false },
            )
        }

        composeRule.onNodeWithTag("start_challenge").performClick()
        composeRule.onNodeWithText("允许相机").performClick()
        composeRule.onNodeWithText("相机权限被拒绝，可再次请求或改用备用挑战")
            .assertIsDisplayed()
        composeRule.onNodeWithText("允许相机").performClick()
        composeRule.onNodeWithTag("landmark_overlay").assertIsDisplayed()
    }

    @Test
    fun temporaryCameraDenialCanUseTheRealSpeechFallback() {
        val repository = SessionRepository(alarm())
        val controller = sessionController(repository)
        val speechSource = ControlledSpeechSource()
        runBlocking { controller.start("alarm") }
        setContent {
            RingingFlowHost(
                controller = controller,
                healthProvider = {
                    readyHealth.copy(camera = HealthStatus.ACTION_REQUIRED)
                },
                permissionRequester = SensorPermissionRequester { _, result -> result(false) },
                isPermissionPermanentlyDenied = { false },
                speechControllerFactory = { SpeechChallengeController(speechSource) },
                speechPhraseProvider = { "今天也要准时起床" },
            )
        }

        composeRule.onNodeWithTag("start_challenge").performClick()
        composeRule.onNodeWithText("允许相机").performClick()
        composeRule.onNodeWithText("备用目标：完整说出语音短句").assertIsDisplayed()
        composeRule.onNodeWithText("改用语音挑战").performClick()

        composeRule.onNodeWithText("目标：完整说出短句").assertIsDisplayed()
        composeRule.onNodeWithText("今天也要准时起床").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(SessionStatus.RINGING, controller.state.value.session?.status)
        }
    }

    @Test
    fun permanentCameraDenialCanUseTheRealSpeechFallback() {
        val repository = SessionRepository(alarm())
        val controller = sessionController(repository)
        val speechSource = ControlledSpeechSource()
        runBlocking { controller.start("alarm") }
        setContent {
            RingingFlowHost(
                controller = controller,
                healthProvider = {
                    readyHealth.copy(camera = HealthStatus.ACTION_REQUIRED)
                },
                permissionRequester = SensorPermissionRequester { _, result -> result(false) },
                isPermissionPermanentlyDenied = { true },
                speechControllerFactory = { SpeechChallengeController(speechSource) },
                speechPhraseProvider = { "今天也要准时起床" },
            )
        }

        composeRule.onNodeWithTag("start_challenge").performClick()
        composeRule.onNodeWithText("允许相机").performClick()
        composeRule.onNodeWithText("打开权限设置").assertIsDisplayed()
        composeRule.onNodeWithText("改用语音挑战").performClick()

        composeRule.onNodeWithText("目标：完整说出短句").assertIsDisplayed()
        composeRule.onNodeWithText("今天也要准时起床").assertIsDisplayed()
    }

    @Test
    fun grantedMicrophonePermissionRefreshesHealthAndEntersSpeechChallenge() {
        val voiceAlarm = alarm().copy(
            challengeType = ChallengeType.VOICE_PHRASE,
            targetCount = 1,
        )
        val repository = SessionRepository(voiceAlarm)
        val controller = sessionController(repository)
        val speechSource = ControlledSpeechSource()
        var health by mutableStateOf(
            readyHealth.copy(microphone = HealthStatus.ACTION_REQUIRED),
        )
        var requestedPermission: String? = null
        runBlocking { controller.start("alarm") }
        setContent {
            RingingFlowHost(
                controller = controller,
                healthProvider = { health },
                permissionRequester = SensorPermissionRequester { permission, result ->
                    requestedPermission = permission
                    health = readyHealth
                    result(true)
                },
                speechControllerFactory = { SpeechChallengeController(speechSource) },
                speechPhraseProvider = { "今天也要准时起床" },
            )
        }

        composeRule.onNodeWithTag("start_challenge").performClick()
        composeRule.onNodeWithText("允许麦克风").performClick()

        composeRule.onNodeWithText("目标：完整说出短句").assertIsDisplayed()
        composeRule.onNodeWithText("今天也要准时起床").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(Manifest.permission.RECORD_AUDIO, requestedPermission)
        }
    }

    @Test
    fun temporaryMicrophoneDenialCanUseTheRealActionFallback() {
        val voiceAlarm = alarm().copy(
            challengeType = ChallengeType.VOICE_PHRASE,
            targetCount = 1,
        )
        val repository = SessionRepository(voiceAlarm)
        val controller = sessionController(repository)
        runBlocking { controller.start("alarm") }
        setContent {
            RingingFlowHost(
                controller = controller,
                healthProvider = {
                    readyHealth.copy(microphone = HealthStatus.ACTION_REQUIRED)
                },
                permissionRequester = SensorPermissionRequester { _, result -> result(false) },
                isPermissionPermanentlyDenied = { false },
                speechPhraseProvider = { "今天也要准时起床" },
            )
        }

        composeRule.onNodeWithTag("start_challenge").performClick()
        composeRule.onNodeWithText("允许麦克风").performClick()
        composeRule.onNodeWithText("备用目标：深蹲 1 次").assertIsDisplayed()
        composeRule.onNodeWithText("改用动作挑战").performClick()

        composeRule.onNodeWithText("深蹲").assertIsDisplayed()
        composeRule.onNodeWithText("0 / 1").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(SessionStatus.RINGING, controller.state.value.session?.status)
        }
    }

    @Test
    fun permanentMicrophoneDenialCanUseTheRealActionFallback() {
        val voiceAlarm = alarm().copy(
            challengeType = ChallengeType.VOICE_PHRASE,
            targetCount = 1,
        )
        val repository = SessionRepository(voiceAlarm)
        val controller = sessionController(repository)
        runBlocking { controller.start("alarm") }
        setContent {
            RingingFlowHost(
                controller = controller,
                healthProvider = {
                    readyHealth.copy(microphone = HealthStatus.ACTION_REQUIRED)
                },
                permissionRequester = SensorPermissionRequester { _, result -> result(false) },
                isPermissionPermanentlyDenied = { true },
                speechPhraseProvider = { "今天也要准时起床" },
            )
        }

        composeRule.onNodeWithTag("start_challenge").performClick()
        composeRule.onNodeWithText("允许麦克风").performClick()
        composeRule.onNodeWithText("打开权限设置").assertIsDisplayed()
        composeRule.onNodeWithText("改用动作挑战").performClick()

        composeRule.onNodeWithText("深蹲").assertIsDisplayed()
        composeRule.onNodeWithText("0 / 1").assertIsDisplayed()
    }

    @Test
    fun permanentMicrophoneDenialSettingsResumeRefreshesIntoSpeechChallenge() {
        val voiceAlarm = alarm().copy(
            challengeType = ChallengeType.VOICE_PHRASE,
            targetCount = 1,
        )
        val repository = SessionRepository(voiceAlarm)
        val controller = sessionController(repository)
        val speechSource = ControlledSpeechSource()
        val lifecycleOwner = ControlledLifecycleOwner()
        var health by mutableStateOf(
            readyHealth.copy(microphone = HealthStatus.ACTION_REQUIRED),
        )
        var repaired: HealthIssue? = null
        lifecycleOwner.handle(Lifecycle.Event.ON_CREATE)
        lifecycleOwner.handle(Lifecycle.Event.ON_START)
        lifecycleOwner.handle(Lifecycle.Event.ON_RESUME)
        runBlocking { controller.start("alarm") }
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                RingingFlowHost(
                    controller = controller,
                    healthProvider = { health },
                    permissionRequester = SensorPermissionRequester { _, result ->
                        result(false)
                    },
                    isPermissionPermanentlyDenied = { true },
                    onRepairHealth = {
                        repaired = it
                        health = readyHealth
                    },
                    speechControllerFactory = { SpeechChallengeController(speechSource) },
                    speechPhraseProvider = { "今天也要准时起床" },
                )
            }
        }

        composeRule.onNodeWithTag("start_challenge").performClick()
        composeRule.onNodeWithText("允许麦克风").performClick()
        composeRule.onNodeWithText("打开权限设置").performClick()
        composeRule.runOnIdle {
            assertEquals(HealthIssue.MICROPHONE, repaired)
            lifecycleOwner.handle(Lifecycle.Event.ON_PAUSE)
            lifecycleOwner.handle(Lifecycle.Event.ON_RESUME)
        }

        composeRule.onNodeWithText("目标：完整说出短句").assertIsDisplayed()
        composeRule.onNodeWithText("今天也要准时起床").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(SessionStatus.RINGING, controller.state.value.session?.status)
        }
    }

    @Test
    fun unavailableSensorsExposeRepairAndRealTenSecondBypass() {
        val repository = SessionRepository(alarm())
        val controller = sessionController(repository)
        runBlocking { controller.start("alarm") }
        var repaired = false
        composeRule.mainClock.autoAdvance = false
        setContent {
            RingingFlowHost(
                controller = controller,
                healthProvider = { unavailableSensors },
                onRepairHealth = { repaired = true },
            )
        }

        composeRule.onNodeWithTag("repair_ringing_sensors").performClick()
        composeRule.runOnIdle {
            assertTrue(repaired)
            assertEquals(SessionStatus.RINGING, controller.state.value.session?.status)
        }
        composeRule.onNodeWithTag("emergency_hold").performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(10_100)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            controller.state.value.session?.status == SessionStatus.BYPASSED
        }
        assertEquals(AlarmEventResult.BYPASSED, repository.events.single().result)
    }

    @Test
    fun speechCompletionFallbackBackAndRestorationUseTheRealHostController() {
        val voiceAlarm = alarm().copy(
            challengeType = ChallengeType.VOICE_PHRASE,
            targetCount = 1,
        )
        val repository = SessionRepository(voiceAlarm)
        val controller = sessionController(repository)
        val speechSource = ControlledSpeechSource()
        runBlocking { controller.start("alarm") }
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            WakeMoveTheme {
                RingingFlowHost(
                    controller = controller,
                    healthProvider = { readyHealth },
                    speechControllerFactory = { SpeechChallengeController(speechSource) },
                    speechPhraseProvider = { "今天也要准时起床" },
                )
            }
        }

        composeRule.onNodeWithTag("start_challenge").performClick()
        composeRule.onNodeWithText("今天也要准时起床").assertIsDisplayed()
        composeRule.onNodeWithText("改用动作挑战").assertIsDisplayed()
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText("今天也要准时起床").assertIsDisplayed()
        Espresso.pressBack()
        composeRule.onNodeWithTag("start_challenge").assertIsDisplayed()
        composeRule.onNodeWithTag("start_challenge").performClick()
        composeRule.runOnIdle {
            speechSource.emit(
                SpeechRecognitionEvent.Final(listOf("今天也要准时起床")),
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            controller.state.value.session?.status == SessionStatus.COMPLETED
        }
        assertEquals(AlarmEventResult.COMPLETED, repository.events.single().result)
    }

    @Test
    fun unavailableSpeechFallbackEntersTheRealCameraChallenge() {
        val voiceAlarm = alarm().copy(
            challengeType = ChallengeType.VOICE_PHRASE,
            targetCount = 1,
        )
        val repository = SessionRepository(voiceAlarm)
        val controller = sessionController(repository)
        runBlocking { controller.start("alarm") }
        setContent {
            RingingFlowHost(
                controller = controller,
                healthProvider = {
                    readyHealth.copy(microphone = HealthStatus.UNAVAILABLE)
                },
                speechPhraseProvider = { "今天也要准时起床" },
            )
        }

        composeRule.onNodeWithTag("start_challenge").performClick()
        composeRule.onNodeWithText("语音服务不可用").assertIsDisplayed()
        composeRule.onNodeWithText("改用动作挑战").performClick()
        composeRule.onNodeWithTag("landmark_overlay").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(SessionStatus.RINGING, controller.state.value.session?.status)
        }
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
                remainingSnoozes = 2,
                onUseSpeechFallback = { fallbackSelected = true },
            )
        }

        composeRule.onNodeWithText("正在响铃").assertIsDisplayed()
        composeRule.onNodeWithText("07:30").assertIsDisplayed()
        composeRule.onNodeWithText("2 / 5").assertIsDisplayed()
        composeRule.onNodeWithText("剩余贪睡 2 次").assertIsDisplayed()
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
                remainingSnoozes = 2,
                onRetry = {},
                onUseCameraFallback = { fallbackSelected = true },
            )
        }

        composeRule.onNodeWithText("正在响铃").assertIsDisplayed()
        composeRule.onNodeWithText("今天也要准时起床").assertIsDisplayed()
        composeRule.onNodeWithText("目标：完整说出短句").assertIsDisplayed()
        composeRule.onNodeWithText("剩余贪睡 2 次").assertIsDisplayed()
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
    fun secondPointerCannotContinueTheInitialEmergencyHold() {
        var bypassed = false
        composeRule.mainClock.autoAdvance = false
        setContent {
            RingingScreen(
                state = ringingState(snoozeCount = 3, remainingSnoozes = 0),
                sensorsUnavailable = true,
                onSnooze = {},
                onStartChallenge = {},
                onEmergencyBypass = { bypassed = true },
                onRepairHealth = {},
            )
        }

        composeRule.onNodeWithTag("emergency_hold").performTouchInput {
            down(pointerId = 0, position = center)
        }
        composeRule.mainClock.advanceTimeBy(5_000)
        composeRule.onNodeWithTag("emergency_hold").performTouchInput {
            down(pointerId = 1, position = center)
            up(pointerId = 0)
        }
        composeRule.mainClock.advanceTimeBy(6_000)
        composeRule.runOnIdle { assertFalse(bypassed) }
    }

    @Test
    fun pointerCancellationImmediatelyResetsEmergencyHold() {
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

        composeRule.onNodeWithTag("emergency_hold").performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(9_000)
        composeRule.onNodeWithTag("emergency_hold").performTouchInput { cancel() }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.runOnIdle { assertFalse(bypassed) }
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
                zoneId = ZoneId.of("Asia/Shanghai"),
            )
        }

        composeRule.onNodeWithText("挑战完成").assertIsDisplayed()
        composeRule.onNodeWithText("紧急停止").assertIsDisplayed()
        composeRule.onNodeWithText("已错过").assertIsDisplayed()
        composeRule.onAllNodesWithText("实际响铃：07-28 07:30:01")
            .assertCountEquals(3)
        composeRule.onAllNodesWithText("完成时间：07-28 07:31:00")
            .assertCountEquals(3)
    }

    @Test
    fun healthRepairActionUsesIssueIdentity() {
        var repaired: HealthIssue? = null
        setContent {
            HealthScreen(
                healthProvider = {
                    HealthSnapshot(
                        exactAlarm = HealthStatus.ACTION_REQUIRED,
                        notifications = HealthStatus.READY,
                        fullScreenIntent = HealthStatus.READY,
                        camera = HealthStatus.READY,
                        microphone = HealthStatus.READY,
                        batteryOptimization = HealthStatus.ACTION_REQUIRED,
                    )
                },
                schedulingProvider = {
                    SchedulerHealthSnapshot(
                        lastResult = SchedulingResult.SUCCESS,
                        nextRegisteredAt = Instant.parse("2026-07-28T00:30:00Z"),
                    )
                },
                onRepair = { repaired = it },
                zoneId = ZoneId.of("Asia/Shanghai"),
            )
        }
        composeRule.onNodeWithTag("repair_exact_alarm").performClick()
        composeRule.runOnIdle { assertEquals(HealthIssue.EXACT_ALARM, repaired) }
        composeRule.onNodeWithText("最近调度：成功").assertIsDisplayed()
        composeRule.onNodeWithText("下次已注册：07-28 08:30").assertIsDisplayed()
        composeRule.onNodeWithTag("repair_battery_optimization").assertIsDisplayed()
    }

    @Test
    fun healthScreenRefreshesHealthSchedulingAndBatteryOnResumeWithoutDuplicates() {
        val lifecycleOwner = ControlledLifecycleOwner()
        var refreshed = false
        var healthReads = 0
        var schedulingReads = 0
        var showHealth by mutableStateOf(true)
        lifecycleOwner.handle(Lifecycle.Event.ON_CREATE)
        lifecycleOwner.handle(Lifecycle.Event.ON_START)
        lifecycleOwner.handle(Lifecycle.Event.ON_RESUME)
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                if (showHealth) {
                    HealthScreen(
                        healthProvider = {
                            healthReads += 1
                            HealthSnapshot(
                                exactAlarm = HealthStatus.READY,
                                notifications = HealthStatus.READY,
                                fullScreenIntent = HealthStatus.READY,
                                camera = HealthStatus.READY,
                                microphone = HealthStatus.READY,
                                batteryOptimization = if (refreshed) {
                                    HealthStatus.READY
                                } else {
                                    HealthStatus.ACTION_REQUIRED
                                },
                            )
                        },
                        schedulingProvider = {
                            schedulingReads += 1
                            if (refreshed) {
                                SchedulerHealthSnapshot(
                                    lastResult = SchedulingResult.SUCCESS,
                                    nextRegisteredAt = Instant.parse("2026-07-28T00:30:00Z"),
                                )
                            } else {
                                SchedulerHealthSnapshot()
                            }
                        },
                        onRepair = {},
                        zoneId = ZoneId.of("Asia/Shanghai"),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("repair_battery_optimization").assertIsDisplayed()
        composeRule.onNodeWithText("最近调度：暂无记录").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(1, healthReads)
            assertEquals(1, schedulingReads)
            refreshed = true
            lifecycleOwner.handle(Lifecycle.Event.ON_PAUSE)
            lifecycleOwner.handle(Lifecycle.Event.ON_RESUME)
        }

        composeRule.onNodeWithTag("repair_battery_optimization").assertDoesNotExist()
        composeRule.onNodeWithText("最近调度：成功").assertIsDisplayed()
        composeRule.onNodeWithText("下次已注册：07-28 08:30").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(2, healthReads)
            assertEquals(2, schedulingReads)
            showHealth = false
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            lifecycleOwner.handle(Lifecycle.Event.ON_PAUSE)
            lifecycleOwner.handle(Lifecycle.Event.ON_RESUME)
            assertEquals(2, healthReads)
            assertEquals(2, schedulingReads)
        }
    }

    @Test
    fun onboardingCompletesFromPrimaryAction() {
        var completed = false
        setContent {
            OnboardingScreen(onComplete = { completed = true })
        }
        composeRule.onNodeWithText("继续").performClick()
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
        val events = mutableListOf<AlarmEvent>()

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

    private class ControlledSpeechSource : SpeechRecognitionSource {
        private var listener: ((SpeechRecognitionEvent) -> Unit)? = null
        override fun start(
            request: SpeechRecognitionRequest,
            listener: (SpeechRecognitionEvent) -> Unit,
        ) {
            this.listener = listener
        }

        fun emit(event: SpeechRecognitionEvent) {
            listener?.invoke(event)
        }
    }

    private class ControlledLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle = registry

        fun handle(event: Lifecycle.Event) {
            registry.handleLifecycleEvent(event)
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
        val unavailableSensors = readyHealth.copy(
            camera = HealthStatus.UNAVAILABLE,
            microphone = HealthStatus.UNAVAILABLE,
        )

        fun sessionController(repository: SessionRepository) = RingingSessionController(
            repository = repository,
            audioPlayer = TestAudioPlayer(),
            vibrator = TestVibrator(),
            scheduler = NoOpScheduler,
        )
    }
}
