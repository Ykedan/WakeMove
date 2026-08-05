package com.wakemove.android.ui.ringing

import com.wakemove.android.i18n.tr

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakemove.android.ringing.RingingUiState
import com.wakemove.android.ui.theme.WakeMoveDawn
import com.wakemove.android.ui.theme.WakeMoveNight
import com.wakemove.android.ui.theme.WakeMoveNightElevated
import com.wakemove.android.ui.theme.WakeMoveSky
import com.wakemove.android.ui.theme.WakeOrbitMark
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable
fun RingingScreen(
    state: RingingUiState,
    sensorsUnavailable: Boolean,
    onSnooze: () -> Unit,
    onStartChallenge: () -> Unit,
    onEmergencyBypass: () -> Unit,
    modifier: Modifier = Modifier,
    onRepairHealth: () -> Unit = {},
) {
    val alarm = state.alarm ?: return
    val session = state.session ?: return
    val formattedTime = alarm.time.format(DateTimeFormatter.ofPattern("HH:mm"))

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(WakeMoveNight, Color(0xFF121B33), Color(0xFF1E2948)),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        .padding(horizontal = 13.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .background(WakeMoveDawn, CircleShape)
                            .padding(4.dp),
                    )
                    Text(
                        text = tr("正在响铃"),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(
                    text = tr("剩余贪睡 ${state.remainingSnoozes} 次"),
                    color = WakeMoveSky,
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(270.dp),
                contentAlignment = Alignment.Center,
            ) {
                WakeOrbitMark(size = 226.dp, dark = true)
                Text(
                    text = formattedTime,
                    color = Color.White,
                    fontSize = 76.sp,
                    lineHeight = 80.sp,
                    letterSpacing = (-2).sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.semantics {
                        contentDescription = tr("闹钟时间 $formattedTime")
                    },
                )
            }
            Text(
                text = alarm.label.ifBlank { tr("WakeMove 闹钟") },
                modifier = Modifier.padding(bottom = 18.dp),
                color = WakeMoveSky,
                style = MaterialTheme.typography.titleMedium,
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = WakeMoveNightElevated.copy(alpha = 0.92f),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = tr("起床任务"),
                        color = WakeMoveDawn,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = challengeLabel(session.challengeType),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = tr("完成目标 ${session.targetCount}，闹钟才会停止"),
                        color = WakeMoveSky.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            state.recoverableError?.let {
                Text(
                    text = it,
                    color = Color(0xFFFFB4AB),
                    modifier = Modifier.padding(top = 12.dp),
                    textAlign = TextAlign.Center,
                )
            }

            Button(
                onClick = onStartChallenge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
                    .height(60.dp)
                    .testTag("start_challenge"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WakeMoveDawn,
                    contentColor = WakeMoveNight,
                ),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(tr("开始挑战"), fontWeight = FontWeight.Bold)
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            if (state.remainingSnoozes > 0) {
                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(54.dp)
                        .testTag("snooze_alarm"),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color.White.copy(alpha = 0.28f),
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(Icons.Rounded.Bedtime, contentDescription = null)
                    Text(
                        text = tr("贪睡一次（还剩 ${state.remainingSnoozes} 次）"),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            if (sensorsUnavailable) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    color = Color(0xFF3A2530),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            tr("相机和麦克风均不可用，请先修复权限或使用紧急停止"),
                            color = Color(0xFFFFC1BC),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(
                            onClick = onRepairHealth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .testTag("repair_ringing_sensors"),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White,
                            ),
                        ) {
                            Text(tr("修复相机和麦克风权限"))
                        }
                    }
                }
            }

            EmergencyHoldButton(
                onComplete = onEmergencyBypass,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun EmergencyHoldButton(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var pointerHolding by remember { mutableStateOf(false) }
    var accessibilityHolding by remember { mutableStateOf(false) }
    val holding = pointerHolding || accessibilityHolding
    LaunchedEffect(holding) {
        if (!holding) {
            progress = 0f
            return@LaunchedEffect
        }
        repeat(EMERGENCY_HOLD_STEPS) { step ->
            delay(EMERGENCY_HOLD_STEP_MS)
            if (!holding) return@LaunchedEffect
            progress = (step + 1).toFloat() / EMERGENCY_HOLD_STEPS
        }
        if (holding) {
            accessibilityHolding = false
            onComplete()
        }
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .border(1.dp, Color.White.copy(alpha = 0.16f), MaterialTheme.shapes.medium)
                .testTag("emergency_hold")
                .semantics {
                    contentDescription = tr("连续按住十秒紧急停止")
                    role = Role.Button
                    stateDescription = if (holding) {
                        tr("倒计时 ${(progress * 10).toInt()} 秒，激活可取消")
                    } else {
                        tr("未开始，激活后需要等待十秒")
                    }
                    liveRegion = LiveRegionMode.Polite
                    onClick(
                        label = if (holding) tr("取消十秒倒计时") else tr("开始十秒倒计时"),
                    ) {
                        accessibilityHolding = !accessibilityHolding
                        true
                    }
                }
                .pointerInput(onComplete) {
                    awaitEachGesture {
                        val initialPointer = awaitFirstDown(requireUnconsumed = false).id
                        pointerHolding = true
                        try {
                            var initialStillPressed: Boolean
                            do {
                                val event = awaitPointerEvent()
                                val initialChange = event.changes
                                    .firstOrNull { it.id == initialPointer }
                                initialStillPressed = initialChange?.pressed == true
                            } while (initialStillPressed)
                        } finally {
                            pointerHolding = false
                        }
                    }
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = tr("按住 10 秒紧急停止"),
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 7.dp)
                .height(3.dp),
            color = WakeMoveDawn,
            trackColor = Color.White.copy(alpha = 0.08f),
        )
    }
}

private fun challengeLabel(type: com.wakemove.android.domain.ChallengeType): String = when (type) {
    com.wakemove.android.domain.ChallengeType.SQUAT -> tr("深蹲")
    com.wakemove.android.domain.ChallengeType.JUMPING_JACK -> tr("开合跳")
    com.wakemove.android.domain.ChallengeType.HANDS_UP -> tr("双手举起")
    com.wakemove.android.domain.ChallengeType.VOICE_PHRASE -> tr("语音短句")
}

private const val EMERGENCY_HOLD_STEPS = 100
private const val EMERGENCY_HOLD_STEP_MS = 100L
