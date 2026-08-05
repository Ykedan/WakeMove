package com.wakemove.android.ui.ringing

import com.wakemove.android.i18n.tr

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import com.wakemove.android.ui.health.HealthIssue
import com.wakemove.android.ui.health.launchHealthRepair
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

fun interface SensorPermissionRequester {
    fun request(permission: String, onResult: (Boolean) -> Unit)
}

private enum class RingingRoute { RINGING, CAMERA, SPEECH }

private enum class PermissionStage { RATIONALE, DENIED, PERMANENTLY_DENIED }

private data class PermissionPrompt(
    val route: RingingRoute,
    val permission: String,
    val issue: HealthIssue,
    val stage: PermissionStage,
) : java.io.Serializable

@Composable
fun RingingFlowHost(
    controller: RingingSessionController,
    healthProvider: () -> HealthSnapshot,
    modifier: Modifier = Modifier,
    permissionRequester: SensorPermissionRequester? = null,
    isPermissionPermanentlyDenied: ((String) -> Boolean)? = null,
    onRepairHealth: ((HealthIssue) -> Unit)? = null,
    speechControllerFactory: ((Context) -> SpeechChallengeController)? = null,
    speechPhraseProvider: ((Context) -> String)? = null,
) {
    val state by controller.state.collectAsState()
    val session = state.session ?: return
    val alarm = state.alarm ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshVersion by remember { mutableIntStateOf(0) }
    val health = key(refreshVersion) { healthProvider() }
    var route by rememberSaveable(session.id) { mutableStateOf(RingingRoute.RINGING) }
    var permissionPrompt by rememberSaveable(session.id) {
        mutableStateOf<PermissionPrompt?>(null)
    }
    val actualPermanentDenial = isPermissionPermanentlyDenied ?: { permission: String ->
        context.findActivity()
            ?.shouldShowRequestPermissionRationale(permission) == false
    }
    val actualRepair = onRepairHealth ?: { issue: HealthIssue ->
        launchHealthRepair(context, issue)
    }
    fun applyPermissionResult(granted: Boolean) {
        val prompt = permissionPrompt ?: return
        refreshVersion += 1
        if (granted) {
            if (healthProvider().statusFor(prompt.issue) == HealthStatus.READY) {
                route = prompt.route
                permissionPrompt = null
            } else {
                permissionPrompt = prompt.copy(stage = PermissionStage.DENIED)
            }
        } else {
            permissionPrompt = prompt.copy(
                stage = if (actualPermanentDenial(prompt.permission)) {
                    PermissionStage.PERMANENTLY_DENIED
                } else {
                    PermissionStage.DENIED
                },
            )
        }
    }
    val activityResultLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
        onResult = ::applyPermissionResult,
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshVersion += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(refreshVersion, permissionPrompt, health) {
        val prompt = permissionPrompt ?: return@LaunchedEffect
        if (health.statusFor(prompt.issue) == HealthStatus.READY) {
            route = prompt.route
            permissionPrompt = null
        }
    }

    fun startChallenge(targetRoute: RingingRoute) {
        val issue = if (targetRoute == RingingRoute.CAMERA) {
            HealthIssue.CAMERA
        } else {
            HealthIssue.MICROPHONE
        }
        when (health.statusFor(issue)) {
            HealthStatus.READY -> route = targetRoute
            HealthStatus.ACTION_REQUIRED -> {
                permissionPrompt = PermissionPrompt(
                    route = targetRoute,
                    permission = issue.permission(),
                    issue = issue,
                    stage = PermissionStage.RATIONALE,
                )
            }
            HealthStatus.UNAVAILABLE -> {
                route = if (health.statusFor(HealthIssue.CAMERA) == HealthStatus.UNAVAILABLE &&
                    health.statusFor(HealthIssue.MICROPHONE) == HealthStatus.UNAVAILABLE
                ) {
                    RingingRoute.RINGING
                } else {
                    targetRoute
                }
            }
        }
    }

    LaunchedEffect(session.id, state.challengeRequested) {
        if (state.challengeRequested && route == RingingRoute.RINGING) {
            startChallenge(
                if (session.challengeType == ChallengeType.VOICE_PHRASE) {
                    RingingRoute.SPEECH
                } else {
                    RingingRoute.CAMERA
                },
            )
        }
    }

    BackHandler(enabled = true) {
        when {
            permissionPrompt != null -> permissionPrompt = null
            route != RingingRoute.RINGING -> route = RingingRoute.RINGING
        }
    }

    permissionPrompt?.let { prompt ->
        val fallbackRoute = when (prompt.issue) {
            HealthIssue.CAMERA -> RingingRoute.SPEECH
            HealthIssue.MICROPHONE -> RingingRoute.CAMERA
            else -> null
        }
        val fallbackAvailable = fallbackRoute != null &&
            health.statusFor(fallbackRoute.healthIssue()) != HealthStatus.UNAVAILABLE
        PermissionRationaleScreen(
            alarmTime = alarm.time.format(DateTimeFormatter.ofPattern("HH:mm")),
            alarmLabel = alarm.label,
            remainingSnoozes = state.remainingSnoozes,
            issue = prompt.issue,
            denied = prompt.stage != PermissionStage.RATIONALE,
            permanentlyDenied = prompt.stage == PermissionStage.PERMANENTLY_DENIED,
            fallbackLabel = if (fallbackAvailable) {
                if (fallbackRoute == RingingRoute.SPEECH) {
                    tr("改用语音挑战")
                } else {
                    tr("改用动作挑战")
                }
            } else {
                null
            },
            fallbackTarget = if (fallbackAvailable) {
                if (fallbackRoute == RingingRoute.SPEECH) {
                    tr("完整说出语音短句")
                } else {
                    tr("${session.challengeType.cameraType().localizedName()} ${session.targetCount} 次")
                }
            } else {
                null
            },
            onRequestPermission = {
                if (permissionRequester == null) {
                    activityResultLauncher.launch(prompt.permission)
                } else {
                    permissionRequester.request(prompt.permission) { granted ->
                        applyPermissionResult(granted)
                    }
                }
            },
            onOpenSettings = { actualRepair(prompt.issue) },
            onUseFallback = {
                permissionPrompt = null
                checkNotNull(fallbackRoute).let(::startChallenge)
            },
            modifier = modifier,
        )
        return
    }

    when (route) {
        RingingRoute.RINGING -> RingingScreen(
            state = state,
            sensorsUnavailable = health.camera != HealthStatus.READY &&
                (
                    health.microphone != HealthStatus.READY ||
                        health.speechRecognition != HealthStatus.READY
                    ),
            onSnooze = { scope.launch { runCatching { controller.snooze() } } },
            onStartChallenge = {
                startChallenge(
                    if (session.challengeType == ChallengeType.VOICE_PHRASE) {
                        RingingRoute.SPEECH
                    } else {
                        RingingRoute.CAMERA
                    },
                )
            },
            onEmergencyBypass = {
                scope.launch { runCatching { controller.bypass() } }
            },
            onRepairHealth = { actualRepair(HealthIssue.CAMERA) },
            modifier = modifier,
        )

        RingingRoute.CAMERA -> {
            if (health.camera == HealthStatus.READY) {
                LiveCameraChallenge(
                    context = context,
                    alarmTime = alarm.time.format(DateTimeFormatter.ofPattern("HH:mm")),
                    alarmLabel = alarm.label,
                    challengeType = session.challengeType.cameraType(),
                    targetCount = session.targetCount,
                    remainingSnoozes = state.remainingSnoozes,
                    onCompleted = {
                        scope.launch { runCatching { controller.complete() } }
                    },
                    onUseSpeechFallback = { startChallenge(RingingRoute.SPEECH) },
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
                    remainingSnoozes = state.remainingSnoozes,
                    onUseSpeechFallback = { startChallenge(RingingRoute.SPEECH) },
                    modifier = modifier,
                )
            }
        }

        RingingRoute.SPEECH -> LiveSpeechChallenge(
            context = context,
            alarmTime = alarm.time.format(DateTimeFormatter.ofPattern("HH:mm")),
            alarmLabel = alarm.label,
            remainingSnoozes = state.remainingSnoozes,
            available = health.microphone == HealthStatus.READY &&
                health.speechRecognition == HealthStatus.READY,
            controllerFactory = speechControllerFactory,
            phraseProvider = speechPhraseProvider,
            onCompleted = {
                scope.launch { runCatching { controller.complete() } }
            },
            onUseCameraFallback = { startChallenge(RingingRoute.CAMERA) },
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
    remainingSnoozes: Int,
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
        remainingSnoozes = remainingSnoozes,
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
    remainingSnoozes: Int,
    available: Boolean,
    controllerFactory: ((Context) -> SpeechChallengeController)?,
    phraseProvider: ((Context) -> String)?,
    onCompleted: () -> Unit,
    onUseCameraFallback: () -> Unit,
    modifier: Modifier,
) {
    val phrase = remember(context, phraseProvider) {
        phraseProvider?.invoke(context) ?: PhraseProvider(context.assets).nextPhrase()
    }
    if (!available) {
        SpeechChallengeScreen(
            alarmTime = alarmTime,
            alarmLabel = alarmLabel,
            state = SpeechChallengeState.PermissionDenied(phrase),
            remainingSnoozes = remainingSnoozes,
            onRetry = {},
            onUseCameraFallback = onUseCameraFallback,
            modifier = modifier,
        )
        return
    }
    val controller = remember(context, phrase, controllerFactory) {
        controllerFactory?.invoke(context) ?: SpeechChallengeController(context)
    }
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
        remainingSnoozes = remainingSnoozes,
        onRetry = controller::retry,
        onUseCameraFallback = onUseCameraFallback,
        modifier = modifier,
    )
}

private fun ChallengeType.cameraType(): ChallengeType =
    if (this == ChallengeType.VOICE_PHRASE) ChallengeType.SQUAT else this

private fun ChallengeType.localizedName(): String = when (this) {
    ChallengeType.SQUAT -> tr("深蹲")
    ChallengeType.JUMPING_JACK -> tr("开合跳")
    ChallengeType.HANDS_UP -> tr("双手举起")
    ChallengeType.VOICE_PHRASE -> tr("语音短句")
}

private fun RingingRoute.healthIssue(): HealthIssue = when (this) {
    RingingRoute.CAMERA -> HealthIssue.CAMERA
    RingingRoute.SPEECH -> HealthIssue.MICROPHONE
    RingingRoute.RINGING -> error("Ringing route has no sensor permission")
}

private fun HealthSnapshot.statusFor(issue: HealthIssue): HealthStatus = when (issue) {
    HealthIssue.CAMERA -> camera
    HealthIssue.MICROPHONE ->
        if (microphone != HealthStatus.READY) microphone else speechRecognition
    HealthIssue.EXACT_ALARM -> exactAlarm
    HealthIssue.NOTIFICATIONS -> notifications
    HealthIssue.NOTIFICATION_CHANNEL -> notificationChannel
    HealthIssue.FULL_SCREEN_INTENT -> fullScreenIntent
    HealthIssue.SPEECH_RECOGNITION -> speechRecognition
}

private fun HealthIssue.permission(): String = when (this) {
    HealthIssue.CAMERA -> Manifest.permission.CAMERA
    HealthIssue.MICROPHONE -> Manifest.permission.RECORD_AUDIO
    else -> error("$this is not a runtime sensor permission")
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
