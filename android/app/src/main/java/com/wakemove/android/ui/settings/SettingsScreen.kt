package com.wakemove.android.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenHealth: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(20.dp)) {
        TextButton(onClick = onBack) { Text("返回") }
        Text("设置", fontSize = 28.sp, modifier = Modifier.padding(vertical = 16.dp))
        Button(
            onClick = onOpenHealth,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("打开健康检查") }
        OutlinedButton(
            onClick = onClearHistory,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) { Text("清除本地历史") }
        Text(
            "相机画面和麦克风音频不会由 WakeMove 保存或上传。",
            modifier = Modifier.padding(top = 24.dp),
        )
        Text("版本 1.0", modifier = Modifier.padding(top = 12.dp))
    }
}
