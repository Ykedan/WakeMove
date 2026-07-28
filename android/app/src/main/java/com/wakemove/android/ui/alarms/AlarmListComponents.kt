package com.wakemove.android.ui.alarms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wakemove.android.domain.Alarm
import com.wakemove.android.domain.ChallengeType
import com.wakemove.android.ui.theme.WakeMoveMutedText
import com.wakemove.android.ui.theme.WakeMovePeach
import com.wakemove.android.ui.theme.WakeMoveSunlight
import com.wakemove.android.ui.theme.WakeMoveSunrise
import com.wakemove.android.ui.theme.WakeMoveText
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun MorningHeader(
    onOpenSettings: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "早上好",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "新的一天，从起床开始",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        IconButton(
            onClick = onOpenSettings,
            enabled = enabled,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .testTag("settings_button"),
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "设置",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
internal fun NextAlarmHero(
    model: NextAlarmUiModel,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val alarmTime = model.occurrence.format(TIME_FORMAT)
    val occurrenceDate = model.occurrence.format(DATE_FORMAT)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                Brush.linearGradient(
                    listOf(WakeMoveSunlight, WakeMoveSunrise),
                ),
            )
            .clickable(
                enabled = enabled,
                onClick = onClick,
                role = Role.Button,
            )
            .testTag("next_alarm_card")
            .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "下一次唤醒",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = WakeMoveText,
            )
            Text(
                text = alarmTime,
                style = MaterialTheme.typography.displayMedium,
                color = WakeMoveText,
            )
            Text(
                text = model.alarm.label.ifBlank { "起床闹钟" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = WakeMoveText,
            )
            Text(
                text = "$occurrenceDate · ${model.alarm.repeatDays.chineseDescription()}",
                style = MaterialTheme.typography.bodyMedium,
                color = WakeMoveText,
            )
        }
    }
}

@Composable
internal fun DisabledAlarmHero() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = WakeMovePeach),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "安静的早晨",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "开启一个闹钟，迎接新的早晨",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SunriseEmptyState(
    onCreateAlarm: () -> Unit,
    enabled: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_alarm_state")
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            modifier = Modifier
                .size(width = 176.dp, height = 112.dp),
        ) {
            val center = Offset(size.width / 2f, size.height * 0.88f)
            val sunRadius = size.minDimension * 0.28f
            drawArc(
                color = WakeMoveSunrise,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(center.x - sunRadius, center.y - sunRadius),
                size = Size(sunRadius * 2f, sunRadius * 2f),
            )

            listOf(200f, 235f, 270f, 305f, 340f).forEach { degrees ->
                val radians = Math.toRadians(degrees.toDouble())
                val rayStart = sunRadius * 1.3f
                val rayEnd = sunRadius * 1.62f
                drawLine(
                    color = WakeMoveSunlight,
                    start = Offset(
                        x = center.x + cos(radians).toFloat() * rayStart,
                        y = center.y + sin(radians).toFloat() * rayStart,
                    ),
                    end = Offset(
                        x = center.x + cos(radians).toFloat() * rayEnd,
                        y = center.y + sin(radians).toFloat() * rayEnd,
                    ),
                    strokeWidth = 5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "还没有闹钟",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "为明天的自己，准备一个温柔的开始",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onCreateAlarm,
            enabled = enabled,
            modifier = Modifier.testTag("add_alarm"),
            colors = ButtonDefaults.buttonColors(
                containerColor = WakeMoveSunrise,
                contentColor = WakeMoveText,
            ),
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
            )
            Text(
                text = "设置第一个闹钟",
                modifier = Modifier.padding(start = 8.dp),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun SunriseAlarmCard(
    alarm: Alarm,
    onEdit: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    val alarmTime = alarm.time.format(TIME_FORMAT)

    Card(
        onClick = onEdit,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("alarm_card_${alarm.id}"),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
                    text = alarmTime,
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = "闹钟时间 $alarmTime"
                    },
                    style = MaterialTheme.typography.displayMedium,
                    color = if (alarm.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        WakeMoveMutedText
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
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = alarm.repeatDays.chineseDescription(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${alarm.challengeType.chineseLabel()} · ${alarm.targetDescription()}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
internal fun AddAlarmButton(
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag("add_alarm"),
        colors = ButtonDefaults.buttonColors(
            containerColor = WakeMoveSunrise,
            contentColor = WakeMoveText,
        ),
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
        )
        Text(
            text = "添加闹钟",
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

internal fun Set<DayOfWeek>.chineseDescription(): String = when {
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

internal fun Alarm.targetDescription(): String =
    if (challengeType == ChallengeType.VOICE_PHRASE) {
        "读完才能关闭"
    } else {
        "$targetCount 次"
    }

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_FORMAT = DateTimeFormatter.ofPattern("M月d日")
