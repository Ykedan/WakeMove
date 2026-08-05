package com.wakemove.android.ui.settings

import com.wakemove.android.i18n.tr

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wakemove.android.BuildConfig
import com.wakemove.android.ui.theme.WakeMoveNight
import com.wakemove.android.ui.theme.WakeMoveSky
import com.wakemove.android.update.AppUpdatePhase
import com.wakemove.android.update.AppUpdateUiState

@Composable
fun SettingsScreen(
    settings: WakeMoveSettings,
    onSettingsChange: (WakeMoveSettings) -> Unit,
    onBack: () -> Unit,
    onOpenHealth: () -> Unit,
    onClearHistory: () -> Unit,
    updateState: AppUpdateUiState = AppUpdateUiState(),
    onCheckUpdate: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = tr("返回"),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column {
                Text(
                    text = "PREFERENCES",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = tr("设置"),
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
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PrivacyTip,
                    contentDescription = null,
                    tint = WakeMoveSky,
                )
                Text(
                    text = tr("你的清晨，只属于你"),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = tr("动作和语音都在本机处理，相机画面与录音不会被保存或上传。"),
                    color = WakeMoveSky,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        SettingsSectionTitle(tr("闹钟可靠性"))
        SettingsGroup {
            SettingsNavigationRow(
                icon = Icons.Rounded.HealthAndSafety,
                title = tr("健康检查"),
                description = tr("检查通知、全屏响铃与挑战权限"),
                value = tr("查看"),
                modifier = Modifier.testTag("settings_health_check"),
                onClick = onOpenHealth,
            )
        }

        SettingsSectionTitle(tr("外观与语言"))
        SettingsGroup {
            SettingsNavigationRow(
                icon = Icons.Rounded.Palette,
                title = tr("显示模式"),
                description = tr("选择浅色、深色或跟随设备"),
                value = settings.theme.displayName(),
                modifier = Modifier.testTag("settings_theme"),
                onClick = { showThemeDialog = true },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsSwitchRow(
                icon = Icons.Rounded.ColorLens,
                title = tr("使用系统主题色"),
                description = if (dynamicColorSupported) {
                    tr("根据设备壁纸调整界面颜色")
                } else {
                    tr("需要 Android 12 或更高版本")
                },
                checked = settings.useDynamicColor && dynamicColorSupported,
                enabled = dynamicColorSupported,
                modifier = Modifier.testTag("settings_dynamic_color"),
                onCheckedChange = { enabled ->
                    onSettingsChange(settings.copy(useDynamicColor = enabled))
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsNavigationRow(
                icon = Icons.Rounded.Language,
                title = tr("应用语言"),
                description = tr("当前完整支持简体中文和英文"),
                value = settings.language.displayName(),
                modifier = Modifier.testTag("settings_language"),
                onClick = { showLanguageDialog = true },
            )
        }

        SettingsSectionTitle(tr("数据与版本"))
        SettingsGroup {
            SettingsNavigationRow(
                icon = Icons.Rounded.SystemUpdateAlt,
                title = tr("应用更新"),
                description = updateDescription(updateState),
                value = updateValue(updateState),
                modifier = Modifier.testTag("settings_app_update"),
                onClick = onCheckUpdate,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsNavigationRow(
                icon = Icons.Rounded.DeleteSweep,
                title = tr("清除响铃历史"),
                description = tr("仅删除本机保存的响铃与挑战记录"),
                value = tr("清除"),
                valueColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("settings_clear_history"),
                onClick = { showClearHistoryDialog = true },
            )
        }

        Text(
            text = tr("WakeMove · 醒动  v${BuildConfig.VERSION_NAME}"),
            modifier = Modifier.padding(top = 18.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = tr("不只叫醒你，还让你真正醒来。"),
            modifier = Modifier.padding(top = 4.dp, bottom = 36.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showThemeDialog) {
        ChoiceDialog(
            title = tr("显示模式"),
            options = ThemePreference.entries,
            selected = settings.theme,
            optionLabel = ThemePreference::displayName,
            onSelected = { selected ->
                onSettingsChange(settings.copy(theme = selected))
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
    }
    if (showLanguageDialog) {
        ChoiceDialog(
            title = tr("应用语言"),
            options = LanguagePreference.entries,
            selected = settings.language,
            optionLabel = LanguagePreference::displayName,
            supportingText = tr("选择后立即切换应用界面语言。"),
            onSelected = { selected ->
                onSettingsChange(settings.copy(language = selected))
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false },
        )
    }
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(tr("清除响铃历史？")) },
            text = { Text(tr("闹钟和设置不会被删除，已清除的历史记录无法恢复。")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearHistory()
                        showClearHistoryDialog = false
                    },
                ) {
                    Text(tr("确认清除"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(tr("取消"))
                }
            },
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(top = 28.dp, bottom = 9.dp, start = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    description: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIcon(icon)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 13.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(value, style = MaterialTheme.typography.labelLarge, color = valueColor)
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIcon(icon)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 13.dp),
        ) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun SettingIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit,
    supportingText: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(option) }
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = { onSelected(option) },
                        )
                        Text(optionLabel(option), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (supportingText != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(tr("完成")) }
        },
    )
}

private fun ThemePreference.displayName(): String = when (this) {
    ThemePreference.FOLLOW_SYSTEM -> tr("跟随系统")
    ThemePreference.LIGHT -> tr("浅色")
    ThemePreference.DARK -> tr("深色")
}

private fun LanguagePreference.displayName(): String = when (this) {
    LanguagePreference.FOLLOW_SYSTEM -> tr("跟随系统")
    LanguagePreference.SIMPLIFIED_CHINESE -> tr("简体中文")
    LanguagePreference.ENGLISH -> "English"
}

private fun updateDescription(state: AppUpdateUiState): String = when (state.phase) {
    AppUpdatePhase.AVAILABLE -> tr("WakeMove v${state.info?.versionName} 已经准备好")
    AppUpdatePhase.DOWNLOADING -> state.progressPercent?.let { tr("正在下载，已完成 $it%") }
        ?: tr("正在下载安装包")
    AppUpdatePhase.READY_TO_INSTALL -> tr("安装包已下载，点击继续安装")
    AppUpdatePhase.INSTALL_PERMISSION_REQUIRED -> tr("需要允许安装应用")
    AppUpdatePhase.ERROR -> tr("上次检查未成功，点击重试")
    AppUpdatePhase.UP_TO_DATE -> tr("当前是最新版本 v${BuildConfig.VERSION_NAME}")
    AppUpdatePhase.CHECKING -> tr("正在连接版本服务")
    AppUpdatePhase.IDLE -> state.message ?: tr("检查新功能和可靠性改进")
}

private fun updateValue(state: AppUpdateUiState): String = when (state.phase) {
    AppUpdatePhase.AVAILABLE -> tr("可更新")
    AppUpdatePhase.DOWNLOADING -> state.progressPercent?.let { "$it%" } ?: tr("连接中")
    AppUpdatePhase.READY_TO_INSTALL -> tr("安装")
    AppUpdatePhase.INSTALL_PERMISSION_REQUIRED -> tr("继续")
    AppUpdatePhase.CHECKING -> tr("检查中")
    AppUpdatePhase.ERROR -> tr("重试")
    AppUpdatePhase.UP_TO_DATE -> tr("最新")
    AppUpdatePhase.IDLE -> tr("检查")
}
