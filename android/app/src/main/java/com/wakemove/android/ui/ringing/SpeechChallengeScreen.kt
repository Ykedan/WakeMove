package com.wakemove.android.ui.ringing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakemove.android.challenge.SpeechChallengeState

@Composable
fun SpeechChallengeScreen(
    alarmTime: String,
    alarmLabel: String,
    state: SpeechChallengeState,
    onRetry: () -> Unit,
    onUseCameraFallback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val phrase = state.phraseOrEmpty()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF111827)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AlarmChallengeHeader(alarmTime, alarmLabel)
        Spacer(Modifier.weight(1f))
        Text(
            state.statusText(),
            color = Color(0xFFFDBA74),
            modifier = Modifier.semantics { contentDescription = "麦克风监听状态" },
        )
        Text(
            phrase,
            color = Color.White,
            fontSize = 30.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(24.dp),
        )
        if (state is SpeechChallengeState.Listening && state.partialText.isNotBlank()) {
            Text("听到：${state.partialText}", color = Color(0xFFD1D5DB))
        }
        Spacer(Modifier.weight(1f))
        if (state.isRetryableUi()) {
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            ) {
                Text("重新聆听")
            }
            OutlinedButton(
                onClick = onUseCameraFallback,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .height(52.dp),
            ) {
                Text("改用动作挑战", color = Color.White)
            }
        }
    }
}

private fun SpeechChallengeState.phraseOrEmpty(): String = when (this) {
    SpeechChallengeState.Idle, SpeechChallengeState.Closed -> ""
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
    is SpeechChallengeState.Listening -> "正在聆听，请清晰读出"
    is SpeechChallengeState.Completed -> "识别成功"
    is SpeechChallengeState.WrongPhrase -> "短句不匹配，请重试"
    is SpeechChallengeState.NetworkError -> "网络异常"
    is SpeechChallengeState.NoMatch -> "没有听清"
    is SpeechChallengeState.PermissionDenied -> "需要麦克风权限"
    is SpeechChallengeState.ServiceUnavailable -> "语音服务不可用"
}

private fun SpeechChallengeState.isRetryableUi(): Boolean = when (this) {
    is SpeechChallengeState.WrongPhrase,
    is SpeechChallengeState.NetworkError,
    is SpeechChallengeState.NoMatch,
    is SpeechChallengeState.PermissionDenied,
    is SpeechChallengeState.ServiceUnavailable,
    -> true
    else -> false
}
