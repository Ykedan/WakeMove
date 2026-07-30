package com.wakemove.android.ui.ringing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakemove.android.challenge.SpeechChallengeState
import com.wakemove.android.ui.theme.WakeMoveDawn
import com.wakemove.android.ui.theme.WakeMoveNight
import com.wakemove.android.ui.theme.WakeMoveNightElevated
import com.wakemove.android.ui.theme.WakeMoveSky

@Composable
fun SpeechChallengeScreen(
    alarmTime: String,
    alarmLabel: String,
    state: SpeechChallengeState,
    remainingSnoozes: Int,
    onRetry: () -> Unit,
    onUseCameraFallback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val phrase = state.phraseOrEmpty()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(WakeMoveNight, Color(0xFF17213B)),
                ),
            )
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AlarmChallengeHeader(alarmTime, alarmLabel, remainingSnoozes)

        Box(
            modifier = Modifier
                .padding(top = 28.dp)
                .height(132.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(124.dp)
                    .border(1.dp, WakeMoveSky.copy(alpha = 0.18f), CircleShape),
            )
            Box(
                modifier = Modifier
                    .size(94.dp)
                    .background(WakeMoveDawn.copy(alpha = 0.13f), CircleShape)
                    .border(1.dp, WakeMoveDawn.copy(alpha = 0.48f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = null,
                    tint = WakeMoveDawn,
                    modifier = Modifier.height(38.dp),
                )
            }
        }

        Text(
            text = "目标：完整说出短句",
            modifier = Modifier.padding(top = 18.dp),
            color = WakeMoveSky,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = state.statusText(),
            color = if (state is SpeechChallengeState.Completed) {
                Color(0xFF8CE6C6)
            } else {
                WakeMoveDawn
            },
            modifier = Modifier
                .padding(top = 6.dp)
                .semantics { contentDescription = "麦克风监听状态" },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 22.dp),
            color = WakeMoveNightElevated,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = phrase,
                    color = Color.White,
                    fontSize = 30.sp,
                    lineHeight = 42.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                if (state is SpeechChallengeState.Listening &&
                    state.partialText.isNotBlank()
                ) {
                    Text(
                        text = "听到：${state.partialText}",
                        color = WakeMoveSky,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                } else if (state is SpeechChallengeState.ServiceUnavailable) {
                    Text(
                        text = "离线语音识别初始化失败，可以重试或改用动作挑战",
                        color = Color(0xFFFFC1BC),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        text = "进度：${state.statusText()}",
                        color = WakeMoveSky.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        if (state.canRetry()) {
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WakeMoveDawn,
                    contentColor = WakeMoveNight,
                ),
            ) {
                Text("重新聆听", fontWeight = FontWeight.Bold)
            }
        }
        OutlinedButton(
            onClick = onUseCameraFallback,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp)
                .height(52.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Color.White.copy(alpha = 0.24f),
            ),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        ) {
            Text("改用动作挑战")
        }
    }
}

private fun SpeechChallengeState.phraseOrEmpty(): String = when (this) {
    SpeechChallengeState.Idle, SpeechChallengeState.Closed -> ""
    is SpeechChallengeState.Preparing -> phrase
    is SpeechChallengeState.Listening -> phrase
    is SpeechChallengeState.Completed -> phrase
    is SpeechChallengeState.WrongPhrase -> phrase
    is SpeechChallengeState.NetworkError -> phrase
    is SpeechChallengeState.NoMatch -> phrase
    is SpeechChallengeState.PermissionDenied -> phrase
    is SpeechChallengeState.ServiceUnavailable -> phrase
}

private fun SpeechChallengeState.statusText(): String = when (this) {
    SpeechChallengeState.Idle -> "准备麦克风"
    SpeechChallengeState.Closed -> "麦克风已关闭"
    is SpeechChallengeState.Preparing -> "正在准备离线语音识别"
    is SpeechChallengeState.Listening -> "正在聆听，请清晰读出"
    is SpeechChallengeState.Completed -> "识别成功"
    is SpeechChallengeState.WrongPhrase -> "短句不匹配，请重试"
    is SpeechChallengeState.NetworkError -> "网络异常"
    is SpeechChallengeState.NoMatch -> "没有听清"
    is SpeechChallengeState.PermissionDenied -> "需要麦克风权限"
    is SpeechChallengeState.ServiceUnavailable -> "离线语音识别初始化失败"
}

private fun SpeechChallengeState.canRetry(): Boolean = when (this) {
    is SpeechChallengeState.WrongPhrase,
    is SpeechChallengeState.NetworkError,
    is SpeechChallengeState.NoMatch,
    is SpeechChallengeState.PermissionDenied,
    is SpeechChallengeState.ServiceUnavailable,
    -> true
    else -> false
}
