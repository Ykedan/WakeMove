package com.wakemove.android.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(28.dp)) {
        Text("WakeMove", fontSize = 42.sp, fontWeight = FontWeight.Bold)
        Text(
            "用动作或语音真正叫醒自己",
            fontSize = 22.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            "继续后，WakeMove 会集中申请通知、相机和麦克风权限，并带你完成精确闹钟与全屏响铃设置。",
            modifier = Modifier.padding(top = 28.dp),
        )
        Text(
            "暂时不同意也可以继续使用，之后可在“健康检查”中补开。",
            modifier = Modifier.padding(top = 16.dp),
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("继续并设置权限") }
    }
}
