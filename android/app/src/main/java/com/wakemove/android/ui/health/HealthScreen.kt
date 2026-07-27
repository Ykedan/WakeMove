package com.wakemove.android.ui.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakemove.android.health.HealthSnapshot
import com.wakemove.android.health.HealthStatus

enum class HealthIssue {
    EXACT_ALARM,
    NOTIFICATIONS,
    FULL_SCREEN_INTENT,
    CAMERA,
    MICROPHONE,
}

@Composable
fun HealthScreen(
    snapshot: HealthSnapshot,
    onRepair: (HealthIssue) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("健康检查", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(if (snapshot.canScheduleAlarms) "闹钟基础能力正常" else "需要修复后才能可靠响铃")
        healthRows(snapshot).forEach { row ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(row.label, fontWeight = FontWeight.Bold)
                        Text(row.status.label())
                    }
                    if (row.status != HealthStatus.READY) {
                        Button(
                            onClick = { onRepair(row.issue) },
                            modifier = Modifier
                                .testTag("repair_${row.issue.tag()}")
                                .semantics {
                                    contentDescription = "修复${row.label}"
                                },
                        ) { Text("去修复") }
                    }
                }
            }
        }
    }
}

private data class HealthRow(
    val issue: HealthIssue,
    val label: String,
    val status: HealthStatus,
)

private fun healthRows(snapshot: HealthSnapshot) = listOf(
    HealthRow(HealthIssue.EXACT_ALARM, "精确闹钟", snapshot.exactAlarm),
    HealthRow(HealthIssue.NOTIFICATIONS, "通知权限", snapshot.notifications),
    HealthRow(HealthIssue.FULL_SCREEN_INTENT, "全屏响铃", snapshot.fullScreenIntent),
    HealthRow(HealthIssue.CAMERA, "相机", snapshot.camera),
    HealthRow(HealthIssue.MICROPHONE, "麦克风", snapshot.microphone),
)

private fun HealthStatus.label(): String = when (this) {
    HealthStatus.READY -> "正常"
    HealthStatus.ACTION_REQUIRED -> "需要操作"
    HealthStatus.UNAVAILABLE -> "设备不可用"
}

private fun HealthIssue.tag(): String = when (this) {
    HealthIssue.EXACT_ALARM -> "exact_alarm"
    HealthIssue.NOTIFICATIONS -> "notifications"
    HealthIssue.FULL_SCREEN_INTENT -> "full_screen_intent"
    HealthIssue.CAMERA -> "camera"
    HealthIssue.MICROPHONE -> "microphone"
}
