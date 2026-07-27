package com.wakemove.android.ui.ringing

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wakemove.android.challenge.CameraChallengeController
import com.wakemove.android.challenge.CameraGuidance
import com.wakemove.android.challenge.ChallengeProgress
import com.wakemove.android.challenge.PhraseProvider
import com.wakemove.android.challenge.PoseLandmarkerAdapter
import com.wakemove.android.challenge.SpeechChallengeController
import com.wakemove.android.challenge.SpeechChallengeState
import com.wakemove.android.domain.ChallengeType
import com.wakemove.android.health.HealthSnapshot
import com.wakemove.android.health.HealthStatus
import com.wakemove.android.ringing.RingingSessionController
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private enum class RingingRoute { RINGING, CAMERA, SPEECH }

@Composable
fun RingingFlowHost(
    controller: RingingSessionController,
    healthProvider: () -> HealthSnapshot,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val session = state.session ?: return
    val alarm = state.alarm ?: return
    val scope = rememberCoroutineScope()
    val health = healthProvider()
    var route by rememberSaveable(session.id) { mutableStateOf(RingingRoute.RINGING) }

    BackHandler(enabled = true) {
        if (route != RingingRoute.RINGING) route = RingingRoute.RINGING
    }

    when (route) {
        RingingRoute.RINGING -> RingingScreen(
            state = state,
            sensorsUnavailable = health.camera != HealthStatus.READY &&
                health.microphone != HealthStatus.READY,
            onSnooze = { scope.launch { runCatching { controller.snooze() } } },
            onStartChallenge = {
                route = if (session.challengeType == ChallengeType.VOICE_PHRASE) {
                    RingingRoute.SPEECH
                } else {
                    RingingRoute.CAMERA
                }
            },
            onEmergencyBypass = { scope.launch { runCatching { controller.bypass() } } },
            modifier = modifier,
        )

        RingingRoute.CAMERA -> {
            if (health.camera == HealthStatus.READY) {
                LiveCameraChallenge(
                    context = androidx.compose.ui.platform.LocalContext.current,
                    alarmTime = alarm.time.format(DateTimeFormatter.ofPattern("HH:mm")),
                    alarmLabel = alarm.label,
                    challengeType = session.challengeType.cameraType(),
                    targetCount = session.targetCount,
                    onCompleted = { scope.launch { runCatching { controller.complete() } } },
                    onUseSpeechFallback = {
                        route = if (health.microphone == HealthStatus.READY) {
                            RingingRoute.SPEECH
                        } else {
                            RingingRoute.RINGING
                        }
                    },
                    modifier = modifier,
                )
            } else {
                CameraChallengeScreen(
                    alarmTime = alarm.time.format(DateTimeFormatter.ofPattern("HH:mm")),
                    alarmLabel = alarm.label,
                    challengeType = session.challengeType.cameraType(),
                    progress = ChallengeProgress(
                        repetitions = 0,
                        targetCount = session.targetCount,
                        guidance = CameraGuidance.NO_PERSON,
                        fallbackAvailable = true,
                    ),
                    landmarks = emptyList(),
                    onUseSpeechFallback = {
                        route = if (health.microphone == HealthStatus.READY) {
                            RingingRoute.SPEECH
                        } else {
                            RingingRoute.RINGING
                        }
                    },
                    modifier = modifier,
                )
            }
        }

        RingingRoute.SPEECH -> LiveSpeechChallenge(
            context = androidx.compose.ui.platform.LocalContext.current,
            alarmTime = alarm.time.format(DateTimeFormatter.ofPattern("HH:mm")),
            alarmLabel = alarm.label,
            available = health.microphone == HealthStatus.READY,
            onCompleted = { scope.launch { runCatching { controller.complete() } } },
            onUseCameraFallback = {
                route = if (health.camera == HealthStatus.READY) {
                    RingingRoute.CAMERA
                } else {
                    RingingRoute.RINGING
                }
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun LiveCameraChallenge(
    context: Context,
    alarmTime: String,
    alarmLabel: String,
    challengeType: ChallengeType,
    targetCount: Int,
    onCompleted: () -> Unit,
    onUseSpeechFallback: () -> Unit,
    modifier: Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember(context) { PreviewView(context) }
    val controller = remember(context, lifecycleOwner, previewView, challengeType, targetCount) {
        CameraChallengeController(
            PoseLandmarkerAdapter(context, lifecycleOwner, previewView),
        )
    }
    val progress by controller.progress.collectAsState()
    DisposableEffect(controller) {
        controller.start(challengeType, targetCount.coerceAtLeast(1))
        onDispose(controller::close)
    }
    LaunchedEffect(progress.completed) {
        if (progress.completed) onCompleted()
    }
    CameraChallengeScreen(
        alarmTime = alarmTime,
        alarmLabel = alarmLabel,
        challengeType = challengeType,
        progress = progress,
        landmarks = progress.landmarks.map { it.x to it.y },
        onUseSpeechFallback = onUseSpeechFallback,
        modifier = modifier,
        cameraPreview = {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

@Composable
private fun LiveSpeechChallenge(
    context: Context,
    alarmTime: String,
    alarmLabel: String,
    available: Boolean,
    onCompleted: () -> Unit,
    onUseCameraFallback: () -> Unit,
    modifier: Modifier,
) {
    val phrase = remember(context) { PhraseProvider(context.assets).nextPhrase() }
    if (!available) {
        SpeechChallengeScreen(
            alarmTime = alarmTime,
            alarmLabel = alarmLabel,
            state = SpeechChallengeState.ServiceUnavailable(phrase),
            onRetry = {},
            onUseCameraFallback = onUseCameraFallback,
            modifier = modifier,
        )
        return
    }
    val controller = remember(context, phrase) { SpeechChallengeController(context) }
    val speechState by controller.state.collectAsState()
    DisposableEffect(controller) {
        controller.start(phrase)
        onDispose(controller::close)
    }
    LaunchedEffect(speechState) {
        if (speechState is SpeechChallengeState.Completed) onCompleted()
    }
    SpeechChallengeScreen(
        alarmTime = alarmTime,
        alarmLabel = alarmLabel,
        state = speechState,
        onRetry = controller::retry,
        onUseCameraFallback = onUseCameraFallback,
        modifier = modifier,
    )
}

private fun ChallengeType.cameraType(): ChallengeType =
    if (this == ChallengeType.VOICE_PHRASE) ChallengeType.SQUAT else this
