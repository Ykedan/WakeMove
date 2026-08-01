package com.wakemove.android.ui.alarms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakemove.android.domain.Alarm
import com.wakemove.android.domain.ChallengeType
import com.wakemove.android.domain.SessionStatus
import com.wakemove.android.ui.theme.WakeMoveMutedText
import com.wakemove.android.ui.theme.WakeMovePeach
import com.wakemove.android.ui.theme.WakeMoveBlue
import com.wakemove.android.ui.theme.WakeMoveDawn
import com.wakemove.android.ui.theme.WakeMoveMist
import com.wakemove.android.ui.theme.WakeMoveNight
import com.wakemove.android.ui.theme.WakeMoveNightElevated
import com.wakemove.android.ui.theme.WakeMoveSky
import com.wakemove.android.ui.theme.WakeOrbitMark
import com.wakemove.android.ui.theme.WakeMoveSunlight
import com.wakemove.android.ui.theme.WakeMoveSunrise
import com.wakemove.android.ui.theme.WakeMoveText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class GreetingCopy(
    val title: String,
    val subtitle: String,
)

internal fun greetingFor(hour: Int): GreetingCopy {
    require(hour in 0..23) { "hour must be between 0 and 23" }
    return when (hour) {
        in 5..10 -> GreetingCopy(
            title = "早上好",
            subtitle = "让今天从真正醒来开始",
        )
        in 11..13 -> GreetingCopy(
            title = "中午好",
            subtitle = "给午后的安排留一个准时提醒",
        )
        in 14..17 -> GreetingCopy(
            title = "下午好",
            subtitle = "把接下来的计划稳稳叫醒",
        )
        else -> GreetingCopy(
            title = "晚上好",
            subtitle = "为明天准备一个可靠的开始",
        )
    }
}

@Composable
internal fun MorningHeader(
    hour: Int,
    date: LocalDate,
    onOpenSettings: () -> Unit,
    enabled: Boolean,
) {
    val greeting = greetingFor(hour)
    val settingsContainerColor = if (enabled) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = DISABLED_CONTAINER_ALPHA)
    }
    val settingsContentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        WakeMoveMutedText.copy(alpha = DISABLED_CONTENT_ALPHA)
    }

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
                text = greeting.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = greeting.subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = date.format(
                    DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE),
                ),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                color = WakeMoveBlue,
            )
        }
        IconButton(
            onClick = onOpenSettings,
            enabled = enabled,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(settingsContainerColor)
                .testTag("settings_button"),
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "设置",
                tint = settingsContentColor,
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
    val heroColors = if (enabled) {
        listOf(WakeMoveNight, Color(0xFF26345F))
    } else {
        listOf(
            WakeMoveSunlight.copy(alpha = DISABLED_GRADIENT_ALPHA),
            WakeMoveSunrise.copy(alpha = DISABLED_GRADIENT_ALPHA),
        )
    }
    val heroContentColor = if (enabled) {
        Color.White
    } else {
        WakeMoveMutedText.copy(alpha = DISABLED_CONTENT_ALPHA)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                Brush.linearGradient(
                    heroColors,
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
        Canvas(Modifier.matchParentSize()) {
            val radius = size.minDimension * 0.58f
            val center = Offset(size.width * 0.93f, size.height * 0.12f)
            drawCircle(
                color = WakeMoveSunlight.copy(alpha = if (enabled) 0.13f else 0.05f),
                radius = radius,
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
            drawCircle(
                color = WakeMoveDawn.copy(alpha = if (enabled) 1f else 0.3f),
                radius = 12.dp.toPx(),
                center = Offset(size.width * 0.86f, size.height * 0.76f),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "NEXT ALARM",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = WakeMoveSky,
                )
                Text(
                    text = "下一次唤醒",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = WakeMoveSunlight,
                )
            }
            Text(
                text = alarmTime,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = 64.sp,
                    lineHeight = 68.sp,
                ),
                color = heroContentColor,
            )
            Text(
                text = model.alarm.label.ifBlank { "起床闹钟" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = heroContentColor,
            )
            Text(
                text = "$occurrenceDate · ${model.alarm.repeatDays.chineseDescription()}",
                style = MaterialTheme.typography.bodyMedium,
                color = heroContentColor,
            )
            Text(
                text = "${model.alarm.challengeType.chineseLabel()} · " +
                    model.alarm.targetDescription(),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (enabled) {
                            WakeMoveNightElevated
                        } else {
                            MaterialTheme.colorScheme.surface.copy(
                                alpha = DISABLED_CONTAINER_ALPHA,
                            )
                        },
                    )
                    .testTag("next_alarm_challenge")
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) WakeMoveSunlight else heroContentColor,
            )
        }
    }
}

@Composable
internal fun DisabledAlarmHero() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = WakeMoveMist),
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
internal fun UnschedulableAlarmHero() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("unschedulable_alarm_hero"),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = WakeMoveMist),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "需要调整时间",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "已启用的闹钟没有可用时间，请重新设置",
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
        WakeOrbitMark(size = 148.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "还没有闹钟",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "用动作或语音挑战，帮你真正清醒地开始一天",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onCreateAlarm,
            enabled = enabled,
            modifier = Modifier.testTag("add_alarm"),
            colors = ButtonDefaults.buttonColors(
                containerColor = WakeMoveBlue,
                contentColor = Color.White,
                disabledContainerColor = WakeMovePeach,
                disabledContentColor = WakeMoveMutedText,
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
    sessionStatus: SessionStatus? = null,
    snoozedUntil: String? = null,
    onChallengeNow: () -> Unit = {},
) {
    val alarmTime = alarm.time.format(TIME_FORMAT)
    val sessionLocked = sessionStatus == SessionStatus.RINGING ||
        sessionStatus == SessionStatus.SNOOZED
    val primaryTextColor = when {
        !enabled -> WakeMoveMutedText.copy(alpha = DISABLED_CONTENT_ALPHA)
        alarm.enabled -> MaterialTheme.colorScheme.onSurface
        else -> WakeMoveMutedText
    }
    val secondaryTextColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        WakeMoveMutedText.copy(alpha = DISABLED_SECONDARY_ALPHA)
    }
    val challengeContainerColor = when {
        !enabled -> WakeMovePeach.copy(alpha = DISABLED_GRADIENT_ALPHA)
        alarm.enabled -> WakeMovePeach
        else -> WakeMovePeach.copy(alpha = DISABLED_CONTAINER_ALPHA)
    }
    val challengeTextColor = when {
        !enabled -> WakeMoveMutedText.copy(alpha = DISABLED_CONTENT_ALPHA)
        alarm.enabled -> MaterialTheme.colorScheme.primary
        else -> WakeMoveMutedText
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled && !sessionLocked,
                onClick = onEdit,
                role = Role.Button,
            )
            .testTag("alarm_card_${alarm.id}"),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = DISABLED_CONTAINER_ALPHA,
            ),
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (alarm.enabled) WakeMoveMist else WakeMoveMist.copy(alpha = 0.6f),
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
                    text = alarmTime,
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = "闹钟时间 $alarmTime"
                    },
                    style = MaterialTheme.typography.displayMedium,
                    color = primaryTextColor,
                )
                Switch(
                    checked = alarm.enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = enabled && !sessionLocked,
                    modifier = Modifier.testTag("alarm_enabled_${alarm.id}"),
                    colors = SwitchDefaults.colors(
                        disabledCheckedThumbColor = WakeMoveMutedText,
                        disabledCheckedTrackColor = WakeMoveSunrise.copy(
                            alpha = DISABLED_GRADIENT_ALPHA,
                        ),
                        disabledUncheckedThumbColor = WakeMoveMutedText.copy(
                            alpha = DISABLED_CONTENT_ALPHA,
                        ),
                        disabledUncheckedTrackColor = WakeMoveMutedText.copy(
                            alpha = DISABLED_GRADIENT_ALPHA,
                        ),
                    ),
                )
            }
            Text(
                text = alarm.label.ifBlank { "起床闹钟" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = primaryTextColor,
            )
            Text(
                text = alarm.repeatDays.chineseDescription(),
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryTextColor,
            )
            Text(
                text = "${alarm.challengeType.chineseLabel()} · ${alarm.targetDescription()}",
                modifier = Modifier
                    .clip(CircleShape)
                    .background(challengeContainerColor)
                    .testTag("alarm_challenge_${alarm.id}")
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = challengeTextColor,
            )
            if (sessionLocked) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .testTag("alarm_locked_${alarm.id}"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (sessionStatus == SessionStatus.SNOOZED) {
                            "贪睡中 · ${snoozedUntil ?: "--:--"} 再响"
                        } else {
                            "正在响铃 · 完成挑战后可修改"
                        },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (sessionStatus == SessionStatus.SNOOZED) {
                        Button(
                            onClick = onChallengeNow,
                            enabled = enabled,
                            modifier = Modifier.testTag("challenge_now_${alarm.id}"),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 16.dp,
                                vertical = 10.dp,
                            ),
                        ) {
                            Text(
                                text = "立即挑战",
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
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
            containerColor = WakeMoveBlue,
            contentColor = Color.White,
            disabledContainerColor = WakeMovePeach,
            disabledContentColor = WakeMoveMutedText,
        ),
    ) {
        Text(
            text = "＋ 添加新闹钟",
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
private const val DISABLED_CONTAINER_ALPHA = 0.55f
private const val DISABLED_CONTENT_ALPHA = 0.58f
private const val DISABLED_SECONDARY_ALPHA = 0.45f
private const val DISABLED_GRADIENT_ALPHA = 0.28f
