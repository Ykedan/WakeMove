package com.wakemove.android.ui.ringing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakemove.android.challenge.CameraGuidance
import com.wakemove.android.challenge.ChallengeProgress
import com.wakemove.android.domain.ChallengeType
import com.wakemove.android.ui.theme.WakeMoveDawn
import com.wakemove.android.ui.theme.WakeMoveNight
import com.wakemove.android.ui.theme.WakeMoveNightElevated
import com.wakemove.android.ui.theme.WakeMoveSky

@Composable
fun CameraChallengeScreen(
    alarmTime: String,
    alarmLabel: String,
    challengeType: ChallengeType,
    progress: ChallengeProgress,
    landmarks: List<Pair<Float, Float>>,
    remainingSnoozes: Int,
    onUseSpeechFallback: () -> Unit,
    modifier: Modifier = Modifier,
    cameraPreview: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WakeMoveNight),
    ) {
        AlarmChallengeHeader(alarmTime, alarmLabel, remainingSnoozes)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
                .semantics { contentDescription = "实时相机画面" },
        ) {
            cameraPreview()
            Canvas(
                Modifier
                    .fillMaxSize()
                    .testTag("landmark_overlay")
                    .semantics { contentDescription = "实时人体关键点轮廓" },
            ) {
                landmarks.forEach { (x, y) ->
                    drawCircle(
                        color = WakeMoveDawn,
                        radius = 7.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(size.width * x, size.height * y),
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp)
                    .testTag("camera_light_hint"),
                color = WakeMoveNight.copy(alpha = 0.88f),
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(
                    text = "请在光线充足处识别，并让全身进入画面",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                text = guidanceText(progress.guidance),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(WakeMoveNight.copy(alpha = 0.88f))
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(challengeName(challengeType), color = WakeMoveDawn)
                Text(
                    "${progress.repetitions} / ${progress.targetCount}",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Button(
                onClick = onUseSpeechFallback,
                enabled = progress.fallbackAvailable,
            ) {
                Text(
                    if (progress.fallbackAvailable) {
                        "改用语音挑战"
                    } else {
                        "60 秒后可改用语音"
                    },
                )
            }
        }
    }
}

@Composable
internal fun AlarmChallengeHeader(
    alarmTime: String,
    alarmLabel: String,
    remainingSnoozes: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .background(WakeMoveNightElevated.copy(alpha = 0.72f))
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                    7.dp,
                ),
            ) {
                Box(
                    Modifier
                        .background(WakeMoveDawn, CircleShape)
                        .padding(4.dp),
                )
                Text(
                    "正在响铃",
                    color = Color.White,
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    alarmTime,
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    alarmLabel,
                    color = WakeMoveSky,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
        Text(
            "剩余贪睡 $remainingSnoozes 次",
            color = WakeMoveSky,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
        )
    }
}

private fun guidanceText(guidance: CameraGuidance): String = when (guidance) {
    CameraGuidance.NONE -> "保持动作稳定，系统会自动计数"
    CameraGuidance.LOW_LIGHT -> "当前光线太暗，请打开灯并面向光源"
    CameraGuidance.NO_PERSON -> "请让全身进入画面"
}

private fun challengeName(type: ChallengeType): String = when (type) {
    ChallengeType.SQUAT -> "深蹲"
    ChallengeType.JUMPING_JACK -> "开合跳"
    ChallengeType.HANDS_UP -> "双手举起"
    ChallengeType.VOICE_PHRASE -> "语音短句"
}
