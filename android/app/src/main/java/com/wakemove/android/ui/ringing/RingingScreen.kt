package com.wakemove.android.ui.ringing

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakemove.android.ringing.RingingUiState
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF111827))
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("正在响铃", color = Color(0xFFFDBA74), fontSize = 18.sp)
        Text(
            alarm.time.format(DateTimeFormatter.ofPattern("HH:mm")),
            color = Color.White,
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics {
                contentDescription =
                    "闹钟时间 ${alarm.time.format(DateTimeFormatter.ofPattern("HH:mm"))}"
            },
        )
        Text(
            alarm.label.ifBlank { "WakeMove 闹钟" },
            color = Color(0xFFF3F4F6),
            fontSize = 22.sp,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "挑战：${challengeLabel(session.challengeType)} · 目标 ${session.targetCount}",
            color = Color(0xFFD1D5DB),
        )
        Text(
            "剩余贪睡 ${state.remainingSnoozes} 次",
            color = Color(0xFFD1D5DB),
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onStartChallenge,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("start_challenge"),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF97316),
                contentColor = Color(0xFF111827),
            ),
        ) {
            Text("开始挑战", fontWeight = FontWeight.Bold)
        }
        if (state.remainingSnoozes > 0) {
            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .height(52.dp)
                    .testTag("snooze_alarm"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) {
                Text("贪睡一次（还剩 ${state.remainingSnoozes} 次）")
            }
        }
        if (sensorsUnavailable) {
            Spacer(Modifier.height(16.dp))
            Text(
                "相机和麦克风均不可用，可连续按住下方按钮 10 秒紧急停止",
                color = Color(0xFFFCA5A5),
                fontSize = 14.sp,
            )
            OutlinedButton(
                onClick = onRepairHealth,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .testTag("repair_ringing_sensors"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) {
                Text("修复相机和麦克风权限")
            }
            EmergencyHoldButton(onEmergencyBypass)
        }
    }
}

@Composable
private fun EmergencyHoldButton(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var holding by remember { mutableStateOf(false) }
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
        if (holding) onComplete()
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(Color(0xFF7F1D1D), RoundedCornerShape(16.dp))
                .testTag("emergency_hold")
                .semantics {
                    contentDescription = "连续按住十秒紧急停止"
                }
                .pointerInput(onComplete) {
                    awaitEachGesture {
                        val initialPointer = awaitFirstDown(requireUnconsumed = false).id
                        holding = true
                        try {
                            var initialStillPressed: Boolean
                            do {
                                val event = awaitPointerEvent()
                                val initialChange = event.changes
                                    .firstOrNull { it.id == initialPointer }
                                initialStillPressed = initialChange?.pressed == true
                            } while (initialStillPressed)
                        } finally {
                            holding = false
                        }
                    }
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("按住 10 秒紧急停止", color = Color.White, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(6.dp),
            color = Color(0xFFFCA5A5),
            trackColor = Color(0xFF374151),
        )
    }
}

private fun challengeLabel(type: com.wakemove.android.domain.ChallengeType): String = when (type) {
    com.wakemove.android.domain.ChallengeType.SQUAT -> "深蹲"
    com.wakemove.android.domain.ChallengeType.JUMPING_JACK -> "开合跳"
    com.wakemove.android.domain.ChallengeType.HANDS_UP -> "双手举起"
    com.wakemove.android.domain.ChallengeType.VOICE_PHRASE -> "语音短句"
}

private const val EMERGENCY_HOLD_STEPS = 100
private const val EMERGENCY_HOLD_STEP_MS = 100L
