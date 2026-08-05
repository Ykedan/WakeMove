package com.wakemove.android.ui.settings

import com.wakemove.android.i18n.tr

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wakemove.android.BuildConfig
import com.wakemove.android.ui.theme.WakeMoveDawn
import com.wakemove.android.ui.theme.WakeMoveNight
import com.wakemove.android.ui.theme.WakeMoveSky
import com.wakemove.android.update.AppUpdatePhase
import com.wakemove.android.update.AppUpdateUiState

@Composable
fun AppUpdateDialog(
    state: AppUpdateUiState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    onIgnoreVersion: () -> Unit,
) {
    if (!state.showDialog) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .testTag("app_update_dialog"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                UpdateDialogHeader(state)
                Spacer(Modifier.height(18.dp))
                UpdateDialogBody(state)
                Spacer(Modifier.height(22.dp))
                UpdateDialogActions(
                    state = state,
                    onDismiss = onDismiss,
                    onDownload = onDownload,
                    onInstall = onInstall,
                    onRetry = onRetry,
                    onIgnoreVersion = onIgnoreVersion,
                )
            }
        }
    }
}

@Composable
private fun UpdateDialogHeader(state: AppUpdateUiState) {
    val icon: ImageVector
    val eyebrow: String
    val title: String
    when (state.phase) {
        AppUpdatePhase.CHECKING -> {
            icon = Icons.Rounded.CloudDownload
            eyebrow = "CHECKING"
            title = tr("正在检查更新")
        }
        AppUpdatePhase.UP_TO_DATE -> {
            icon = Icons.Rounded.CheckCircle
            eyebrow = "UP TO DATE"
            title = tr("当前已是最新版本")
        }
        AppUpdatePhase.AVAILABLE -> {
            icon = Icons.Rounded.NewReleases
            eyebrow = "NEW RELEASE"
            title = tr("WakeMove v${state.info?.versionName} 可以更新")
        }
        AppUpdatePhase.DOWNLOADING -> {
            icon = Icons.Rounded.CloudDownload
            eyebrow = "DOWNLOADING"
            title = tr("正在下载 WakeMove v${state.info?.versionName}")
        }
        AppUpdatePhase.READY_TO_INSTALL -> {
            icon = Icons.Rounded.InstallMobile
            eyebrow = "READY"
            title = tr("更新已经准备好")
        }
        AppUpdatePhase.INSTALL_PERMISSION_REQUIRED -> {
            icon = Icons.Rounded.InstallMobile
            eyebrow = "ONE MORE STEP"
            title = tr("允许 WakeMove 安装更新")
        }
        AppUpdatePhase.ERROR -> {
            icon = Icons.Rounded.ErrorOutline
            eyebrow = "TRY AGAIN"
            title = tr("这次没有检查成功")
        }
        AppUpdatePhase.IDLE -> return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (state.phase == AppUpdatePhase.CHECKING) {
                CircularProgressIndicator(modifier = Modifier.size(25.dp), strokeWidth = 2.5.dp)
            } else {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Column {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun UpdateDialogBody(state: AppUpdateUiState) {
    when (state.phase) {
        AppUpdatePhase.AVAILABLE -> {
            VersionTrack(latestVersion = state.info?.versionName.orEmpty())
            Spacer(Modifier.height(16.dp))
            Text(
                text = tr("本次更新"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = state.info?.releaseNotes.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AppUpdatePhase.DOWNLOADING -> {
            val progress = state.progressPercent
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(9.dp))
                Text(
                    text = tr("已下载 $progress%"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(9.dp))
                Text(tr("正在获取安装包大小…"))
            }
            Text(
                text = tr("可以先返回使用，下载完成后 WakeMove 会提醒你安装。"),
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AppUpdatePhase.READY_TO_INSTALL -> Text(
            text = tr("下一步会打开 Android 系统安装界面。更新会覆盖旧版本，闹钟和设置会保留。"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppUpdatePhase.INSTALL_PERMISSION_REQUIRED -> Text(
            text = state.message ?: tr("在系统设置中允许安装后，返回 WakeMove 继续。"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppUpdatePhase.ERROR -> Text(
            text = state.message ?: tr("请检查网络后重试。"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppUpdatePhase.UP_TO_DATE -> Text(
            text = tr("你正在使用 WakeMove v${BuildConfig.VERSION_NAME}。"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppUpdatePhase.CHECKING -> Text(
            text = tr("正在连接版本服务，通常只需要几秒。"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppUpdatePhase.IDLE -> Unit
    }
}

@Composable
private fun VersionTrack(latestVersion: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = WakeMoveNight),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(tr("当前版本"), color = WakeMoveSky, style = MaterialTheme.typography.bodySmall)
                Text(
                    "v${BuildConfig.VERSION_NAME}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
            HorizontalDivider(
                modifier = Modifier.weight(0.55f),
                thickness = 2.dp,
                color = WakeMoveDawn,
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End,
            ) {
                Text(tr("可用版本"), color = WakeMoveSky, style = MaterialTheme.typography.bodySmall)
                Text("v$latestVersion", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ColumnScope.UpdateDialogActions(
    state: AppUpdateUiState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    onIgnoreVersion: () -> Unit,
) {
    when (state.phase) {
        AppUpdatePhase.AVAILABLE -> {
            Button(
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth().testTag("download_update"),
            ) {
                Text(tr("下载并更新"))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onIgnoreVersion) { Text(tr("忽略此版本")) }
                TextButton(onClick = onDismiss) { Text(tr("稍后提醒")) }
            }
        }
        AppUpdatePhase.READY_TO_INSTALL,
        AppUpdatePhase.INSTALL_PERMISSION_REQUIRED,
        -> {
            Button(
                onClick = onInstall,
                modifier = Modifier.fillMaxWidth().testTag("install_update"),
            ) {
                Text(if (state.phase == AppUpdatePhase.READY_TO_INSTALL) tr("安装更新") else tr("再次尝试"))
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(tr("稍后"))
            }
        }
        AppUpdatePhase.ERROR -> {
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(tr("重新检查")) }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(tr("关闭"))
            }
        }
        AppUpdatePhase.DOWNLOADING -> Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(tr("在后台下载"))
        }
        AppUpdatePhase.CHECKING -> TextButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.End),
        ) { Text(tr("关闭")) }
        AppUpdatePhase.UP_TO_DATE -> Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(tr("知道了")) }
        AppUpdatePhase.IDLE -> Unit
    }
}
