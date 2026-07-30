package com.wakemove.android.ui.alarms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wakemove.android.domain.Alarm
import com.wakemove.android.domain.RingingSession
import com.wakemove.android.domain.SessionStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime

@Composable
fun AlarmListScreen(
    alarms: List<Alarm>,
    activeSession: RingingSession? = null,
    operationState: AlarmOperationUiState = AlarmOperationUiState(),
    onCreateAlarm: () -> Unit,
    onEditAlarm: (Alarm) -> Unit,
    onEnabledChange: (Alarm, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    nowProvider: () -> ZonedDateTime = { ZonedDateTime.now() },
) {
    val now = nowProvider()
    val nextAlarm = remember(alarms, now) { findNextEnabledAlarm(alarms, now) }
    val interactionsEnabled = !operationState.isInFlight

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 18.dp,
            bottom = 120.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            MorningHeader(
                onOpenSettings = {
                    if (interactionsEnabled) {
                        onOpenSettings()
                    }
                },
                enabled = interactionsEnabled,
            )
        }

        if (activeSession?.status == SessionStatus.SNOOZED) {
            item {
                val nextTime = activeSession.pendingScheduleAt
                    ?.atZone(ZoneId.systemDefault())
                    ?.format(DateTimeFormatter.ofPattern("HH:mm"))
                    ?: "--:--"
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("snoozed_alarm_banner"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                        Text(
                            text = "贪睡中，下次 $nextTime",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "完成挑战后才能修改或关闭这个闹钟",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        item {
            when {
                alarms.isEmpty() -> SunriseEmptyState(
                    onCreateAlarm = {
                        if (interactionsEnabled) {
                            onCreateAlarm()
                        }
                    },
                    enabled = interactionsEnabled,
                )
                nextAlarm != null -> NextAlarmHero(
                    model = nextAlarm,
                    onClick = {
                        if (interactionsEnabled) {
                            onEditAlarm(nextAlarm.alarm)
                        }
                    },
                    enabled = interactionsEnabled,
                )
                alarms.any(Alarm::enabled) -> UnschedulableAlarmHero()
                else -> DisabledAlarmHero()
            }
        }

        operationState.errorMessage?.let { message ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "我的闹钟",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "${alarms.size} 个",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(items = alarms, key = Alarm::id) { alarm ->
            val sessionLocked = activeSession?.alarmId == alarm.id
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SunriseAlarmCard(
                    alarm = alarm,
                    onEdit = {
                        if (interactionsEnabled && !sessionLocked) {
                            onEditAlarm(alarm)
                        }
                    },
                    onEnabledChange = { enabled ->
                        if (interactionsEnabled && !sessionLocked) {
                            onEnabledChange(alarm, enabled)
                        }
                    },
                    enabled = interactionsEnabled && !sessionLocked,
                )
                if (sessionLocked) {
                    Text(
                        text = "响铃会话进行中，完成挑战后可修改",
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .testTag("alarm_locked_${alarm.id}"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (alarms.isNotEmpty()) {
            item {
                AddAlarmButton(
                    onClick = {
                        if (interactionsEnabled) {
                            onCreateAlarm()
                        }
                    },
                    enabled = interactionsEnabled,
                )
            }
        }
    }
}
