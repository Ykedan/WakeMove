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
import androidx.compose.material3.Button
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

@Composable
fun CameraChallengeScreen(
    alarmTime: String,
    alarmLabel: String,
    challengeType: ChallengeType,
    progress: ChallengeProgress,
    landmarks: List<Pair<Float, Float>>,
    onUseSpeechFallback: () -> Unit,
    modifier: Modifier = Modifier,
    cameraPreview: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF111827)),
    ) {
        AlarmChallengeHeader(alarmTime, alarmLabel)
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
                        color = Color(0xFFFFA94D),
                        radius = 7.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(size.width * x, size.height * y),
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
            }
            Text(
                text = guidanceText(progress.guidance),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(Color(0xCC111827))
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
                Text(challengeName(challengeType), color = Color(0xFFFDBA74))
                Text(
                    "${progress.repetitions} / ${progress.targetCount}",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (progress.fallbackAvailable) {
                Button(onClick = onUseSpeechFallback) {
                    Text("改用语音挑战")
                }
            }
        }
    }
}

@Composable
internal fun AlarmChallengeHeader(alarmTime: String, alarmLabel: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            alarmTime,
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            alarmLabel,
            color = Color(0xFFD1D5DB),
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

private fun guidanceText(guidance: CameraGuidance): String = when (guidance) {
    CameraGuidance.NONE -> "保持动作稳定，系统会自动计数"
    CameraGuidance.LOW_LIGHT -> "光线不足，请打开灯并面向光源"
    CameraGuidance.NO_PERSON -> "请让全身进入画面"
}

private fun challengeName(type: ChallengeType): String = when (type) {
    ChallengeType.SQUAT -> "深蹲"
    ChallengeType.JUMPING_JACK -> "开合跳"
    ChallengeType.HANDS_UP -> "双手举起"
    ChallengeType.VOICE_PHRASE -> "语音短句"
}
