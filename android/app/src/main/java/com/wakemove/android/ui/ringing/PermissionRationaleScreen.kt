package com.wakemove.android.ui.ringing

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.CameraAlt
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wakemove.android.ui.health.HealthIssue
import com.wakemove.android.ui.theme.WakeMoveDawn
import com.wakemove.android.ui.theme.WakeMoveNight
import com.wakemove.android.ui.theme.WakeMoveNightElevated
import com.wakemove.android.ui.theme.WakeMoveSky

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
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(WakeMoveNight, Color(0xFF17213B))),
            )
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AlarmChallengeHeader(alarmTime, alarmLabel, remainingSnoozes)

        Box(
            modifier = Modifier
                .padding(top = 42.dp)
                .size(92.dp)
                .background(WakeMoveDawn.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (issue == HealthIssue.CAMERA) {
                    Icons.Rounded.CameraAlt
                } else {
                    Icons.Rounded.Mic
                },
                contentDescription = null,
                tint = WakeMoveDawn,
                modifier = Modifier.size(38.dp),
            )
        }
        Text(
            text = if (issue == HealthIssue.CAMERA) {
                "相机只在动作挑战期间启用"
            } else {
                "麦克风只在语音挑战期间启用"
            },
            modifier = Modifier.padding(top = 22.dp, start = 24.dp, end = 24.dp),
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (issue == HealthIssue.CAMERA) {
                "动作识别完全在本机完成，WakeMove 不保存或上传相机画面。"
            } else {
                "录音只交给内置 Vosk 离线识别，WakeMove 不保存或上传音频。"
            },
            modifier = Modifier.padding(horizontal = 30.dp, vertical = 12.dp),
            color = WakeMoveSky,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )

        if (denied) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                color = WakeMoveNightElevated,
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = if (permanentlyDenied) {
                            "请在系统设置开启${issue.sensorName()}权限"
                        } else {
                            "${issue.sensorName()}权限被拒绝，可再次请求或改用备用挑战"
                        },
                        color = Color(0xFFFFC1BC),
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (fallbackLabel != null && fallbackTarget != null) {
                        Text(
                            text = "备用目标：$fallbackTarget",
                            color = WakeMoveSky,
                        )
                        OutlinedButton(
                            onClick = onUseFallback,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White,
                            ),
                        ) {
                            Text(fallbackLabel)
                        }
                    }
                }
            }
        }

        Button(
            onClick = if (permanentlyDenied) onOpenSettings else onRequestPermission,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 22.dp)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = WakeMoveDawn,
                contentColor = WakeMoveNight,
            ),
        ) {
            Text(
                text = if (permanentlyDenied) {
                    "打开权限设置"
                } else if (issue == HealthIssue.CAMERA) {
                    "允许相机"
                } else {
                    "允许麦克风"
                },
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun HealthIssue.sensorName(): String = when (this) {
    HealthIssue.CAMERA -> "相机"
    HealthIssue.MICROPHONE -> "麦克风"
    else -> ""
}
