package com.wakemove.android.ui.ringing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wakemove.android.ui.health.HealthIssue

@Composable
internal fun PermissionRationaleScreen(
    alarmTime: String,
    alarmLabel: String,
    remainingSnoozes: Int,
    issue: HealthIssue,
    denied: Boolean,
    permanentlyDenied: Boolean,
    fallbackLabel: String?,
    fallbackTarget: String?,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onUseFallback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().background(Color(0xFF111827)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AlarmChallengeHeader(alarmTime, alarmLabel, remainingSnoozes)
        Spacer(Modifier.weight(1f))
        Text(
            if (issue == HealthIssue.CAMERA) {
                "相机只在动作挑战期间启用"
            } else {
                "麦克风只在语音挑战期间启用"
            },
            color = Color.White,
            modifier = Modifier.padding(24.dp),
        )
        Text(
            if (issue == HealthIssue.CAMERA) {
                "动作识别在本机完成，WakeMove 不保存或上传相机画面。"
            } else {
                "WakeMove 不保存麦克风录音；系统语音服务可能联网识别。"
            },
            color = Color(0xFFD1D5DB),
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        if (denied) {
            Text(
                if (permanentlyDenied) {
                    "请在系统设置开启${issue.sensorName()}权限"
                } else {
                    "${issue.sensorName()}权限被拒绝，可再次请求或改用备用挑战"
                },
                color = Color(0xFFFCA5A5),
                modifier = Modifier.padding(24.dp),
            )
        }
        if (denied && fallbackLabel != null && fallbackTarget != null) {
            Text(
                "备用目标：$fallbackTarget",
                color = Color(0xFFD1D5DB),
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            OutlinedButton(
                onClick = onUseFallback,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Text(fallbackLabel, color = Color.White)
            }
        }
        Spacer(Modifier.weight(1f))
        if (!permanentlyDenied) {
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) {
                Text(if (issue == HealthIssue.CAMERA) "允许相机" else "允许麦克风")
            }
        }
        if (permanentlyDenied) {
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            ) {
                Text("打开权限设置", color = Color.White)
            }
        }
    }
}

private fun HealthIssue.sensorName(): String = when (this) {
    HealthIssue.CAMERA -> "相机"
    HealthIssue.MICROPHONE -> "麦克风"
    else -> ""
}
