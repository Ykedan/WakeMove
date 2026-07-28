package com.wakemove.android.ui.alarms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wakemove.android.domain.ChallengeType
import com.wakemove.android.ui.theme.WakeMoveErrorContainer
import com.wakemove.android.ui.theme.WakeMovePeach
import com.wakemove.android.ui.theme.WakeMoveSunlight
import com.wakemove.android.ui.theme.WakeMoveSunrise
import com.wakemove.android.ui.theme.WakeMoveText
import java.time.DayOfWeek

@Composable
internal fun SunriseTimeCard(
    timeText: String,
    isTimeValid: Boolean,
    onTimeChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                Brush.linearGradient(
                    listOf(WakeMoveSunlight, WakeMoveSunrise),
                ),
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "时间",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = WakeMoveText,
        )
        OutlinedTextField(
            value = timeText,
            onValueChange = onTimeChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("alarm_time"),
            textStyle = MaterialTheme.typography.displayMedium.copy(
                textAlign = TextAlign.Center,
            ),
            placeholder = {
                Text(
                    text = "07:30",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            },
            supportingText = {
                if (isTimeValid) {
                    Text("24 小时制")
                } else {
                    Text("请选择有效时间")
                }
            },
            isError = !isTimeValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            singleLine = true,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeekdaySelector(
    selectedDays: Set<DayOfWeek>,
    onDayToggle: (DayOfWeek) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DayOfWeek.entries.forEach { day ->
            val selected = day in selectedDays
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (selected) WakeMoveSunrise else WakeMovePeach)
                    .toggleable(
                        value = selected,
                        role = Role.Checkbox,
                        onValueChange = { onDayToggle(day) },
                    )
                    .testTag("weekday_${day.name}")
                    .semantics {
                        this.selected = selected
                        role = Role.Checkbox
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = day.shortChineseLabel(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = WakeMoveText,
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(16.dp)
                            .testTag("weekday_selected_marker_${day.name}"),
                        tint = WakeMoveText,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ChallengeSelector(
    selectedChallenge: ChallengeType,
    onChallengeSelected: (ChallengeType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChallengeType.entries.forEach { type ->
            val selected = selectedChallenge == type
            val shape = MaterialTheme.shapes.medium
            val selectedForeground = MaterialTheme.colorScheme.primary
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(
                        if (selected) {
                            WakeMovePeach
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    )
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) selectedForeground else WakeMovePeach,
                        shape = shape,
                    )
                    .clickable(
                        role = Role.RadioButton,
                        onClick = { onChallengeSelected(type) },
                    )
                    .testTag("challenge_${type.name}")
                    .semantics {
                        this.selected = selected
                        role = Role.RadioButton
                    }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(WakeMovePeach),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = type.challengeIcon(),
                        contentDescription = null,
                        tint = if (selected) selectedForeground else WakeMoveSunrise,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = type.chineseLabel(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = type.chineseDescription(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "已选择",
                        tint = selectedForeground,
                    )
                }
            }
        }
    }
}

@Composable
internal fun TargetStepper(
    count: Int,
    onCountChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("target_count"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "目标次数",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onCountChange(count - 1) },
                enabled = count > 1,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(WakeMovePeach)
                    .testTag("target_decrease"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Remove,
                    contentDescription = "减少目标次数",
                    tint = if (count > 1) WakeMoveText else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    },
                )
            }
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            IconButton(
                onClick = { onCountChange(count + 1) },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(WakeMovePeach)
                    .testTag("target_increase"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "增加目标次数",
                    tint = WakeMoveText,
                )
            }
        }
    }
}

@Composable
internal fun EditorAlertCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = WakeMoveErrorContainer),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
internal fun EditorCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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

private fun ChallengeType.chineseDescription(): String = when (this) {
    ChallengeType.SQUAT,
    ChallengeType.JUMPING_JACK,
    ChallengeType.HANDS_UP,
    -> "完成指定次数后关闭"

    ChallengeType.VOICE_PHRASE -> "正确朗读指定短语后关闭"
}

private fun ChallengeType.challengeIcon(): ImageVector = when (this) {
    ChallengeType.SQUAT -> Icons.Outlined.FitnessCenter
    ChallengeType.JUMPING_JACK -> Icons.AutoMirrored.Outlined.DirectionsRun
    ChallengeType.HANDS_UP -> Icons.Outlined.AccessibilityNew
    ChallengeType.VOICE_PHRASE -> Icons.Outlined.RecordVoiceOver
}
