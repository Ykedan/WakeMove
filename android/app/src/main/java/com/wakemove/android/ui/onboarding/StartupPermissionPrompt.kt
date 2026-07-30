package com.wakemove.android.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wakemove.android.health.HealthSnapshot
import com.wakemove.android.health.HealthStatus
import com.wakemove.android.ui.health.HealthIssue
import com.wakemove.android.ui.health.fallbackAppSettingsIntent
import com.wakemove.android.ui.health.healthRepairIntent
import com.wakemove.android.ui.theme.WakeMoveBlue
import com.wakemove.android.ui.theme.WakeMoveMist
import com.wakemove.android.ui.theme.WakeMoveNight

@Composable
fun StartupPermissionPrompt(
    healthProvider: () -> HealthSnapshot,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    var authorizationStarted by remember { mutableStateOf(false) }
    var runtimeRequestFinished by remember { mutableStateOf(false) }
    var specialIssues by remember { mutableStateOf(emptyList<HealthIssue>()) }
    var specialIndex by remember { mutableIntStateOf(0) }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        specialIndex += 1
    }
    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        runtimeRequestFinished = true
    }

    LaunchedEffect(runtimeRequestFinished, specialIndex, specialIssues) {
        if (!runtimeRequestFinished) return@LaunchedEffect
        val issue = specialIssues.getOrNull(specialIndex)
        if (issue == null) {
            onFinished()
            return@LaunchedEffect
        }
        val intent = runCatching { healthRepairIntent(context, issue) }
            .getOrNull()
            ?.takeIf { it.resolveActivity(context.packageManager) != null }
            ?: fallbackAppSettingsIntent(context)
        settingsLauncher.launch(intent)
    }

    AlertDialog(
        onDismissRequest = {
            if (!authorizationStarted) onFinished()
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(WakeMoveBlue, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AlarmOn,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
                Text(
                    text = if (authorizationStarted) {
                        "正在完成权限设置"
                    } else {
                        "先让闹钟准时出现"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (authorizationStarted) {
                    Text(
                        text = "请在系统页面完成当前设置，然后返回 WakeMove。",
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text(
                        text = "只需要一次设置，之后 WakeMove 才能在锁屏时准时叫醒你。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PermissionStep(
                        icon = Icons.Rounded.NotificationsActive,
                        title = "通知与全屏响铃",
                        description = "到点立即出现，不错过闹钟",
                    )
                    PermissionStep(
                        icon = Icons.Rounded.CameraAlt,
                        title = "相机",
                        description = "只用于本机动作识别",
                    )
                    PermissionStep(
                        icon = Icons.Rounded.Mic,
                        title = "麦克风",
                        description = "只用于本机离线语音挑战",
                    )
                    Text(
                        text = "暂时不同意也可以继续，之后可在“健康检查”中补开。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (authorizationStarted) return@Button
                    authorizationStarted = true
                    specialIssues = startupSpecialIssues(healthProvider())
                    val permissions = missingRuntimePermissions(context)
                    if (permissions.isEmpty()) {
                        runtimeRequestFinished = true
                    } else {
                        runtimeLauncher.launch(permissions.toTypedArray())
                    }
                },
                enabled = !authorizationStarted,
                colors = ButtonDefaults.buttonColors(
                    containerColor = WakeMoveBlue,
                    contentColor = Color.White,
                ),
            ) {
                Text(if (authorizationStarted) "请完成系统设置" else "开始授权")
            }
        },
        dismissButton = {
            if (!authorizationStarted) {
                TextButton(onClick = onFinished) {
                    Text("以后再说")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 0.dp,
    )
}

@Composable
private fun PermissionStep(
    icon: ImageVector,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WakeMoveMist, MaterialTheme.shapes.medium)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = WakeMoveBlue,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = WakeMoveNight,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun missingRuntimePermissions(
    context: Context,
    apiLevel: Int = Build.VERSION.SDK_INT,
): List<String> = buildList {
    if (apiLevel >= Build.VERSION_CODES.TIRAMISU &&
        !context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
    ) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (!context.hasPermission(Manifest.permission.CAMERA)) {
        add(Manifest.permission.CAMERA)
    }
    if (!context.hasPermission(Manifest.permission.RECORD_AUDIO)) {
        add(Manifest.permission.RECORD_AUDIO)
    }
}

internal fun startupSpecialIssues(snapshot: HealthSnapshot): List<HealthIssue> = buildList {
    if (snapshot.exactAlarm == HealthStatus.ACTION_REQUIRED) {
        add(HealthIssue.EXACT_ALARM)
    }
    if (snapshot.fullScreenIntent == HealthStatus.ACTION_REQUIRED) {
        add(HealthIssue.FULL_SCREEN_INTENT)
    }
}

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
