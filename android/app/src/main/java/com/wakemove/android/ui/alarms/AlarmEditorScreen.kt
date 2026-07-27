package com.wakemove.android.ui.alarms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakemove.android.domain.ChallengeType
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorScreen(
    state: AlarmEditorUiState,
    operationState: AlarmOperationUiState = AlarmOperationUiState(),
    onTimeChange: (String) -> Unit,
    onDayToggle: (DayOfWeek) -> Unit,
    onChallengeSelected: (ChallengeType) -> Unit,
    onTargetCountChange: (Int) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    navigationEnabled: Boolean = true,
    onLabelChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(if (state.alarmId == null) "新建闹钟" else "编辑闹钟")
                },
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        enabled = navigationEnabled,
                    ) {
                        Text("返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "时间",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedTextField(
                        value = state.timeText,
                        onValueChange = onTimeChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("alarm_time"),
                        textStyle = MaterialTheme.typography.displayMedium.copy(
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        placeholder = { Text("07:30") },
                        supportingText = {
                            if (state.parsedTime == null) {
                                Text("请选择有效时间")
                            } else {
                                Text("24 小时制")
                            }
                        },
                        isError = state.parsedTime == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.label,
                        onValueChange = onLabelChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("标签（可选）") },
                        singleLine = true,
                    )
                }
            }

            EditorSection(title = "重复") {
                WeekdayFlow(
                    state = state,
                    onDayToggle = onDayToggle,
                )
                Text(
                    text = if (state.selectedDays.isEmpty()) "仅响一次" else "按所选日期重复",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            EditorSection(title = "起床挑战") {
                ChallengeType.entries.forEach { type ->
                    val selected = state.challengeType == type
                    FilterChip(
                        selected = selected,
                        onClick = { onChallengeSelected(type) },
                        label = { Text(type.chineseLabel()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("challenge_${type.name}")
                            .semantics { this.selected = selected },
                    )
                }
                if (state.challengeType != ChallengeType.VOICE_PHRASE) {
                    OutlinedTextField(
                        value = state.targetCount.toString(),
                        onValueChange = { value ->
                            value.toIntOrNull()?.let(onTargetCountChange)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("target_count"),
                        label = { Text("目标次数") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
            }

            state.healthMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            operationState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Button(
                onClick = onSave,
                enabled = state.canSave && !operationState.isInFlight,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("save_alarm"),
            ) {
                if (operationState.isInFlight) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .height(24.dp)
                            .width(24.dp)
                            .testTag("submission_progress"),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("保存中…")
                } else {
                    Text("保存闹钟")
                }
            }

            if (state.alarmId != null) {
                OutlinedButton(
                    onClick = { showDeleteConfirmation = true },
                    enabled = !operationState.isInFlight,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text("删除闹钟", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("确认删除？") },
            text = { Text("删除后将无法恢复此闹钟。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                    modifier = Modifier.testTag("confirm_delete"),
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekdayFlow(
    state: AlarmEditorUiState,
    onDayToggle: (DayOfWeek) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DayOfWeek.entries.forEach { day ->
            val selected = day in state.selectedDays
            FilterChip(
                selected = selected,
                onClick = { onDayToggle(day) },
                label = { Text(day.shortChineseLabel()) },
                modifier = Modifier
                    .width(48.dp)
                    .heightIn(min = 48.dp)
                    .testTag("weekday_${day.name}")
                    .semantics {
                        this.selected = selected
                        role = Role.Checkbox
                    },
            )
        }
    }
}

@Composable
private fun EditorSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

private fun DayOfWeek.shortChineseLabel(): String = when (this) {
    DayOfWeek.MONDAY -> "一"
    DayOfWeek.TUESDAY -> "二"
    DayOfWeek.WEDNESDAY -> "三"
    DayOfWeek.THURSDAY -> "四"
    DayOfWeek.FRIDAY -> "五"
    DayOfWeek.SATURDAY -> "六"
    DayOfWeek.SUNDAY -> "日"
}

internal fun ChallengeType.chineseLabel(): String = when (this) {
    ChallengeType.SQUAT -> "深蹲"
    ChallengeType.JUMPING_JACK -> "开合跳"
    ChallengeType.HANDS_UP -> "双手举高"
    ChallengeType.VOICE_PHRASE -> "朗读短语"
}
