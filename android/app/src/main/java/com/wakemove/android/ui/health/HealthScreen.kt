package com.wakemove.android.ui.health

import com.wakemove.android.i18n.tr

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import com.wakemove.android.ui.theme.WakeMoveBlue
import com.wakemove.android.ui.theme.WakeMoveDawn
import com.wakemove.android.ui.theme.WakeMoveMist
import com.wakemove.android.ui.theme.WakeMoveNight
import com.wakemove.android.ui.theme.WakeMoveSky

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
    onBack: (() -> Unit)? = null,
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
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = tr("返回设置"),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Column {
                Text(
                    text = "SYSTEM CHECK",
                    style = MaterialTheme.typography.labelLarge,
                    color = WakeMoveBlue,
                )
                Text(
                    text = tr("健康检查"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = WakeMoveNight),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (snapshot.canScheduleAlarms) {
                                    Color(0xFF71D8B6)
                                } else {
                                    WakeMoveDawn
                                },
                                CircleShape,
                            ),
                    )
                    Text(
                        text = if (snapshot.canScheduleAlarms) {
                            tr("闹钟基础能力正常")
                        } else {
                            tr("需要修复后才能可靠响铃")
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Text(
                    text = tr("最近调度：${scheduling.lastResult.label()}"),
                    color = WakeMoveSky,
                )
                Text(
                    text = scheduling.nextRegisteredAt?.let {
                        tr("下次已注册：${
                            it.atZone(zoneId)
                                .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                        }")
                    } ?: tr("下次已注册：暂无"),
                    color = WakeMoveSky,
                )
            }
        }
        Surface(
            color = WakeMoveMist,
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                text = tr("重启后需先完成首次解锁，才会恢复闹钟调度；首次解锁前暂不支持。"),
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        scheduling.latestDelivery?.let { delivery ->
            Text(
                text = tr("最近投递：${delivery.stage.chineseLabel()} · ${
                    delivery.stageAt.atZone(zoneId)
                        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
                }"),
                modifier = Modifier.testTag("latest_delivery_stage"),
            )
            if (delivery.stage == DeliveryStage.FAILED) {
                Text(
                    text = tr(
                        "失败位置：${delivery.failureStage?.chineseLabel() ?: "未知"}" +
                            (delivery.failureClass?.let { "（$it）" } ?: ""),
                    ),
                    modifier = Modifier.testTag("latest_delivery_failure"),
                )
            }
        }
        healthRows(snapshot).forEach { row ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                if (row.status == HealthStatus.READY) {
                                    Color(0xFFE0F5EE)
                                } else {
                                    Color(0xFFFFE4E0)
                                },
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (row.status == HealthStatus.READY) "✓" else "!",
                            color = if (row.status == HealthStatus.READY) {
                                Color(0xFF176B55)
                            } else {
                                Color(0xFF9E3827)
                            },
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            row.label,
                            modifier = Modifier.padding(start = 12.dp),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            row.status.label(),
                            modifier = Modifier.padding(start = 12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
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
                                    contentDescription = tr("修复${row.label}")
                                },
                        ) { Text(tr("去修复")) }
                    }
                }
            }
        }
    }
    if (showNotificationRationale) {
        AlertDialog(
            onDismissRequest = { showNotificationRationale = false },
            title = { Text(tr("允许响铃通知")) },
            text = {
                Text(tr("WakeMove 需要通知权限来持续显示正在响铃的闹钟和挑战入口。"))
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
                    Text(tr("继续授权"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationRationale = false }) {
                    Text(tr("暂不"))
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
    HealthRow(HealthIssue.EXACT_ALARM, tr("精确闹钟"), snapshot.exactAlarm),
    HealthRow(HealthIssue.NOTIFICATIONS, tr("通知权限"), snapshot.notifications),
    HealthRow(
        HealthIssue.NOTIFICATION_CHANNEL,
        tr("响铃通知通道"),
        snapshot.notificationChannel,
    ),
    HealthRow(HealthIssue.FULL_SCREEN_INTENT, tr("全屏响铃"), snapshot.fullScreenIntent),
    HealthRow(HealthIssue.CAMERA, tr("相机"), snapshot.camera),
    HealthRow(HealthIssue.MICROPHONE, tr("麦克风"), snapshot.microphone),
    HealthRow(
        HealthIssue.SPEECH_RECOGNITION,
        tr("离线语音识别"),
        snapshot.speechRecognition,
    ),
)

private fun HealthStatus.label(): String = when (this) {
    HealthStatus.READY -> tr("正常")
    HealthStatus.ACTION_REQUIRED -> tr("需要操作")
    HealthStatus.UNAVAILABLE -> tr("设备不可用")
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
    SchedulingResult.NEVER -> tr("暂无记录")
    SchedulingResult.SUCCESS -> tr("登记成功")
    SchedulingResult.FAILURE -> tr("登记失败")
}

private fun DeliveryStage.chineseLabel(): String = when (this) {
    DeliveryStage.REGISTERED -> tr("已登记到系统")
    DeliveryStage.DELIVERED -> tr("目标时间已投递")
    DeliveryStage.NEXT_REPEAT_REGISTERED -> tr("下一次重复已登记")
    DeliveryStage.SERVICE_START_REQUESTED -> tr("已请求启动响铃")
    DeliveryStage.SERVICE_STARTED -> tr("响铃服务已启动")
    DeliveryStage.AUDIO_STARTED -> tr("声音已开始")
    DeliveryStage.RINGING -> tr("正在响铃")
    DeliveryStage.SNOOZED -> tr("正在贪睡")
    DeliveryStage.COMPLETED -> tr("挑战已完成")
    DeliveryStage.BYPASSED -> tr("已紧急停止")
    DeliveryStage.MISSED -> tr("已错过")
    DeliveryStage.FAILED -> tr("投递失败")
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
