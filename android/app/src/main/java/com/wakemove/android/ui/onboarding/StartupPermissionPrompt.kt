package com.wakemove.android.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wakemove.android.health.HealthSnapshot
import com.wakemove.android.health.HealthStatus
import com.wakemove.android.ui.health.HealthIssue
import com.wakemove.android.ui.health.fallbackAppSettingsIntent
import com.wakemove.android.ui.health.healthRepairIntent

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
            Text(
                text = if (authorizationStarted) "正在完成权限设置" else "先让闹钟准时出现",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("WakeMove 会依次申请通知、相机和麦克风权限。")
                Text("随后会打开精确闹钟、全屏响铃和电池限制设置；每完成一项返回即可继续。")
                if (authorizationStarted) {
                    Text(
                        text = "请在系统页面完成当前设置，然后返回 WakeMove。",
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text("如果暂时不同意，可以稍后在“健康检查”中补开。")
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
    )
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
    if (snapshot.batteryOptimization == HealthStatus.ACTION_REQUIRED) {
        add(HealthIssue.BATTERY_OPTIMIZATION)
    }
}

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
