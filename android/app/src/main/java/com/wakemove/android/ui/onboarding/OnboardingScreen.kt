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
            "闹钟会按需使用通知、全屏显示、相机或麦克风。动作识别在本机完成，WakeMove 不保存相机画面或麦克风录音。",
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
        ) { Text("开始使用") }
    }
}
