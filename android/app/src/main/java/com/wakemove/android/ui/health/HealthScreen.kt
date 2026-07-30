package com.wakemove.android.ui.health

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.wakemove.android.health.HealthSnapshot
import com.wakemove.android.health.HealthStatus
import com.wakemove.android.scheduling.SchedulerHealthSnapshot
import com.wakemove.android.scheduling.SchedulingResult
import com.wakemove.android.scheduling.DeliveryStage
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class HealthIssue {
    EXACT_ALARM,
    NOTIFICATIONS,
    NOTIFICATION_CHANNEL,
    FULL_SCREEN_INTENT,
    CAMERA,
    MICROPHONE,
    SPEECH_RECOGNITION,
}

data class NotificationPermissionUiState(
    val permissionGranted: Boolean,
    val requestedBefore: Boolean,
    val shouldShowRationale: Boolean,
)

@Composable
fun HealthScreen(
    healthProvider: () -> HealthSnapshot,
    schedulingProvider: () -> SchedulerHealthSnapshot,
    onRepair: (HealthIssue) -> Unit,
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault(),
    notificationPermissionState: (() -> NotificationPermissionUiState)? = null,
    requestNotificationPermission: (((Boolean) -> Unit) -> Unit)? = null,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val activity = context.findActivity()
    var refreshVersion by remember { mutableIntStateOf(0) }
    var showNotificationRationale by remember { mutableStateOf(false) }
    val notificationPreferences = remember(context) {
        context.getSharedPreferences("wakemove_permission_history", Context.MODE_PRIVATE)
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        refreshVersion += 1
    }
    val snapshot = remember(refreshVersion) { healthProvider() }
    val scheduling = remember(refreshVersion) { schedulingProvider() }
    DisposableEffect(lifecycleOwner) {
        var skipInitialResume = lifecycleOwner.lifecycle.currentState
            .isAtLeast(Lifecycle.State.RESUMED)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (skipInitialResume) {
                    skipInitialResume = false
                } else {
                    refreshVersion += 1
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("健康检查", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(if (snapshot.canScheduleAlarms) "闹钟基础能力正常" else "需要修复后才能可靠响铃")
        Text("最近调度：${scheduling.lastResult.label()}")
        Text("重启后需先完成首次解锁，才会恢复闹钟调度；首次解锁前暂不支持。")
        Text(
            scheduling.nextRegisteredAt?.let {
                "下次已注册：${
                    it.atZone(zoneId)
                        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                }"
            } ?: "下次已注册：暂无",
        )
        scheduling.latestDelivery?.let { delivery ->
            Text(
                text = "最近投递：${delivery.stage.chineseLabel()} · ${
                    delivery.stageAt.atZone(zoneId)
                        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
                }",
                modifier = Modifier.testTag("latest_delivery_stage"),
            )
            if (delivery.stage == DeliveryStage.FAILED) {
                Text(
                    text = "失败位置：${delivery.failureStage?.chineseLabel() ?: "未知"}" +
                        (delivery.failureClass?.let { "（$it）" } ?: ""),
                    modifier = Modifier.testTag("latest_delivery_failure"),
                )
            }
        }
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
                            onClick = {
                                if (row.issue == HealthIssue.NOTIFICATIONS) {
                                    val permissionState = notificationPermissionState?.invoke()
                                        ?: NotificationPermissionUiState(
                                            permissionGranted = Build.VERSION.SDK_INT <
                                                Build.VERSION_CODES.TIRAMISU ||
                                                ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.POST_NOTIFICATIONS,
                                                ) == PackageManager.PERMISSION_GRANTED,
                                            requestedBefore = notificationPreferences.getBoolean(
                                                "post_notifications_requested",
                                                false,
                                            ),
                                            shouldShowRationale = activity
                                                ?.shouldShowRequestPermissionRationale(
                                                    Manifest.permission.POST_NOTIFICATIONS,
                                                ) == true,
                                        )
                                    when (notificationRepairAction(
                                        apiLevel = Build.VERSION.SDK_INT,
                                        permissionGranted = permissionState.permissionGranted,
                                        requestedBefore = permissionState.requestedBefore,
                                        shouldShowRationale = permissionState.shouldShowRationale,
                                    )) {
                                        NotificationRepairAction.SHOW_RATIONALE ->
                                            showNotificationRationale = true
                                        NotificationRepairAction.OPEN_SETTINGS ->
                                            onRepair(row.issue)
                                    }
                                } else {
                                    onRepair(row.issue)
                                }
                            },
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
    if (showNotificationRationale) {
        AlertDialog(
            onDismissRequest = { showNotificationRationale = false },
            title = { Text("允许响铃通知") },
            text = {
                Text("WakeMove 需要通知权限来持续显示正在响铃的闹钟和挑战入口。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNotificationRationale = false
                        notificationPreferences.edit()
                            .putBoolean("post_notifications_requested", true)
                            .apply()
                        if (requestNotificationPermission == null) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            requestNotificationPermission {
                                refreshVersion += 1
                            }
                        }
                    },
                ) {
                    Text("继续授权")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationRationale = false }) {
                    Text("暂不")
                }
            },
        )
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
    HealthRow(
        HealthIssue.NOTIFICATION_CHANNEL,
        "响铃通知通道",
        snapshot.notificationChannel,
    ),
    HealthRow(HealthIssue.FULL_SCREEN_INTENT, "全屏响铃", snapshot.fullScreenIntent),
    HealthRow(HealthIssue.CAMERA, "相机", snapshot.camera),
    HealthRow(HealthIssue.MICROPHONE, "麦克风", snapshot.microphone),
    HealthRow(
        HealthIssue.SPEECH_RECOGNITION,
        "离线语音识别",
        snapshot.speechRecognition,
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
    HealthIssue.NOTIFICATION_CHANNEL -> "notification_channel"
    HealthIssue.FULL_SCREEN_INTENT -> "full_screen_intent"
    HealthIssue.CAMERA -> "camera"
    HealthIssue.MICROPHONE -> "microphone"
    HealthIssue.SPEECH_RECOGNITION -> "speech_recognition"
}

private fun SchedulingResult.label(): String = when (this) {
    SchedulingResult.NEVER -> "暂无记录"
    SchedulingResult.SUCCESS -> "登记成功"
    SchedulingResult.FAILURE -> "登记失败"
}

private fun DeliveryStage.chineseLabel(): String = when (this) {
    DeliveryStage.REGISTERED -> "已登记到系统"
    DeliveryStage.DELIVERED -> "目标时间已投递"
    DeliveryStage.NEXT_REPEAT_REGISTERED -> "下一次重复已登记"
    DeliveryStage.SERVICE_START_REQUESTED -> "已请求启动响铃"
    DeliveryStage.SERVICE_STARTED -> "响铃服务已启动"
    DeliveryStage.AUDIO_STARTED -> "声音已开始"
    DeliveryStage.RINGING -> "正在响铃"
    DeliveryStage.SNOOZED -> "正在贪睡"
    DeliveryStage.COMPLETED -> "挑战已完成"
    DeliveryStage.BYPASSED -> "已紧急停止"
    DeliveryStage.MISSED -> "已错过"
    DeliveryStage.FAILED -> "投递失败"
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
