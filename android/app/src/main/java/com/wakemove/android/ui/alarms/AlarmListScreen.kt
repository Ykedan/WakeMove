package com.wakemove.android.ui.alarms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakemove.android.domain.Alarm
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter

@Composable
fun AlarmListScreen(
    alarms: List<Alarm>,
    operationState: AlarmOperationUiState = AlarmOperationUiState(),
    onCreateAlarm: () -> Unit,
    onEditAlarm: (Alarm) -> Unit,
    onEnabledChange: (Alarm, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "早上好",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "闹钟",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                TextButton(
                    onClick = onOpenSettings,
                    enabled = !operationState.isInFlight,
                ) {
                    Text("设置")
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (!operationState.isInFlight) {
                        onCreateAlarm()
                    }
                },
                modifier = Modifier.testTag("add_alarm"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text("＋", fontSize = 28.sp)
            }
        },
    ) { padding ->
        if (alarms.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("还没有闹钟", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "添加一个，让动作把你叫醒",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 8.dp,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                operationState.errorMessage?.let { message ->
                    item {
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
                items(items = alarms, key = Alarm::id) { alarm ->
                    AlarmCard(
                        alarm = alarm,
                        onEdit = { onEditAlarm(alarm) },
                        onEnabledChange = { enabled ->
                            onEnabledChange(alarm, enabled)
                        },
                        enabled = !operationState.isInFlight,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmCard(
    alarm: Alarm,
    onEdit: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    Card(
        onClick = onEdit,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("alarm_card_${alarm.id}"),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = alarm.time.format(TIME_FORMAT),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (alarm.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Switch(
                    checked = alarm.enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = enabled,
                    modifier = Modifier.testTag("alarm_enabled_${alarm.id}"),
                )
            }
            Text(
                text = alarm.label.ifBlank { "起床闹钟" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = alarm.repeatDays.chineseDescription(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${alarm.challengeType.chineseLabel()} · ${alarm.targetDescription()}",
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun Set<DayOfWeek>.chineseDescription(): String = when {
    isEmpty() -> "仅响一次"
    size == 7 -> "每天"
    this == setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
    ) -> "工作日"
    else -> sortedBy(DayOfWeek::getValue).joinToString("、") { day ->
        when (day) {
            DayOfWeek.MONDAY -> "周一"
            DayOfWeek.TUESDAY -> "周二"
            DayOfWeek.WEDNESDAY -> "周三"
            DayOfWeek.THURSDAY -> "周四"
            DayOfWeek.FRIDAY -> "周五"
            DayOfWeek.SATURDAY -> "周六"
            DayOfWeek.SUNDAY -> "周日"
        }
    }
}

private fun Alarm.targetDescription(): String =
    if (challengeType == com.wakemove.android.domain.ChallengeType.VOICE_PHRASE) {
        "读完才能关闭"
    } else {
        "$targetCount 次"
    }

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
