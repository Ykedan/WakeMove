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
import com.wakemove.android.scheduling.SchedulerHealthSnapshot
import com.wakemove.android.scheduling.SchedulingResult
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class HealthIssue {
    EXACT_ALARM,
    NOTIFICATIONS,
    FULL_SCREEN_INTENT,
    CAMERA,
    MICROPHONE,
    BATTERY_OPTIMIZATION,
}

@Composable
fun HealthScreen(
    snapshot: HealthSnapshot,
    onRepair: (HealthIssue) -> Unit,
    modifier: Modifier = Modifier,
    scheduling: SchedulerHealthSnapshot = SchedulerHealthSnapshot(),
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("健康检查", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(if (snapshot.canScheduleAlarms) "闹钟基础能力正常" else "需要修复后才能可靠响铃")
        Text("最近调度：${scheduling.lastResult.label()}")
        Text(
            scheduling.nextRegisteredAt?.let {
                "下次已注册：${
                    it.atZone(zoneId)
                        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                }"
            } ?: "下次已注册：暂无",
        )
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
    HealthRow(
        HealthIssue.BATTERY_OPTIMIZATION,
        "电池优化",
        snapshot.batteryOptimization,
    ),
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
    HealthIssue.BATTERY_OPTIMIZATION -> "battery_optimization"
}

private fun SchedulingResult.label(): String = when (this) {
    SchedulingResult.NEVER -> "暂无记录"
    SchedulingResult.SUCCESS -> "成功"
    SchedulingResult.FAILURE -> "失败"
}
