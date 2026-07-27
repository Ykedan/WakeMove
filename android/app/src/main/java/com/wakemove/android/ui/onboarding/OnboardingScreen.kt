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
            "本页只是说明，不代表已经授权。只有你明确开始动作或语音挑战时，WakeMove 才会先说明用途，再请求相机或麦克风权限。",
            modifier = Modifier.padding(top = 28.dp),
        )
        Text(
            "若相机和麦克风都不可用，响铃页会提供连续按住 10 秒的紧急停止。",
            modifier = Modifier.padding(top = 16.dp),
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("继续") }
    }
}
