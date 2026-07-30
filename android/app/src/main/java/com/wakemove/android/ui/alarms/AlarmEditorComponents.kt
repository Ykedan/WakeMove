package com.wakemove.android.ui.alarms

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wakemove.android.domain.ChallengeType
import com.wakemove.android.domain.VibrationIntensity
import com.wakemove.android.domain.VibrationPattern
import com.wakemove.android.ringing.AlarmSound
import com.wakemove.android.ringing.AlarmSoundCatalog
import com.wakemove.android.ui.theme.WakeMoveErrorContainer
import com.wakemove.android.ui.theme.WakeMovePeach
import com.wakemove.android.ui.theme.WakeMoveSunlight
import com.wakemove.android.ui.theme.WakeMoveSunrise
import com.wakemove.android.ui.theme.WakeMoveText
import java.time.DayOfWeek

@Composable
internal fun SunriseTimeCard(
    hour: Int,
    minute: Int,
    nextOccurrenceLabel: String,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
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
        TimeWheelPicker(
            hour = hour,
            minute = minute,
            onTimeChange = onTimeChange,
        )
        Text(
            text = nextOccurrenceLabel,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("next_occurrence_preview"),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = WakeMoveText.copy(alpha = 0.78f),
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
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (selected) WakeMoveSunrise else WakeMovePeach),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = day.shortChineseLabel(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = WakeMoveText,
                    )
                }
                if (selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.background)
                            .padding(2.dp)
                            .testTag("weekday_selected_marker_${day.name}"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = WakeMoveText,
                        )
                    }
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
internal fun SoundAndVibrationCard(
    soundId: String,
    vibrationEnabled: Boolean,
    vibrationPattern: VibrationPattern,
    vibrationIntensity: VibrationIntensity,
    onOpenSoundPicker: () -> Unit,
    onVibrationEnabledChange: (Boolean) -> Unit,
    onVibrationPatternSelected: (VibrationPattern) -> Unit,
    onVibrationIntensitySelected: (VibrationIntensity) -> Unit,
    onPreviewVibration: (VibrationPattern, VibrationIntensity) -> Unit,
) {
    val sound = AlarmSoundCatalog.find(soundId)
    EditorCard(title = "声音与震动") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(WakeMovePeach.copy(alpha = 0.55f))
                .clickable(onClick = onOpenSoundPicker)
                .testTag("open_sound_picker")
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(WakeMoveSunlight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = WakeMoveText,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = sound.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = sound.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SoundWaveform(sound = sound, selected = true)
        }

        HorizontalDivider(color = WakeMovePeach)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "震动",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (vibrationEnabled) "响铃时同步震动" else "仅播放铃声",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = vibrationEnabled,
                onCheckedChange = onVibrationEnabledChange,
                modifier = Modifier.testTag("vibration_enabled"),
            )
        }

        if (vibrationEnabled) {
            VibrationChoiceRow(
                label = "震动频率",
                options = VibrationPattern.entries,
                selected = vibrationPattern,
                optionLabel = VibrationPattern::chineseLabel,
                testTagPrefix = "vibration_pattern",
                onSelected = {
                    onVibrationPatternSelected(it)
                    onPreviewVibration(it, vibrationIntensity)
                },
            )
            VibrationChoiceRow(
                label = "震动力度",
                options = VibrationIntensity.entries,
                selected = vibrationIntensity,
                optionLabel = VibrationIntensity::chineseLabel,
                testTagPrefix = "vibration_intensity",
                onSelected = {
                    onVibrationIntensitySelected(it)
                    onPreviewVibration(vibrationPattern, it)
                },
            )
            Text(
                text = "点击选项可感受一次震动",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun <T> VibrationChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    testTagPrefix: String,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    label = { Text(optionLabel(option)) },
                    modifier = Modifier.testTag(
                        "${testTagPrefix}_${option.toString().lowercase()}",
                    ),
                )
            }
        }
    }
}

@Composable
internal fun SoundSelectionDialog(
    selectedSoundId: String,
    previewingSoundId: String?,
    onSelect: (String) -> Unit,
    onPreview: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择唤醒铃声") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "四段原创舒缓声景，点击播放键试听",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AlarmSoundCatalog.sounds.forEach { sound ->
                    val selected = sound.id == selectedSoundId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(
                                if (selected) WakeMovePeach else {
                                    MaterialTheme.colorScheme.surface
                                },
                            )
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    WakeMovePeach
                                },
                                shape = MaterialTheme.shapes.medium,
                            )
                            .clickable { onSelect(sound.id) }
                            .testTag("sound_${sound.id}")
                            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SoundWaveform(sound = sound, selected = selected)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = sound.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = sound.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { onPreview(sound.id) },
                            modifier = Modifier.testTag("preview_${sound.id}"),
                        ) {
                            Icon(
                                imageVector = if (previewingSoundId == sound.id) {
                                    Icons.Rounded.Stop
                                } else {
                                    Icons.Rounded.PlayArrow
                                },
                                contentDescription = if (previewingSoundId == sound.id) {
                                    "停止试听${sound.name}"
                                } else {
                                    "试听${sound.name}"
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        },
    )
}

@Composable
private fun SoundWaveform(sound: AlarmSound, selected: Boolean) {
    val color = if (selected) MaterialTheme.colorScheme.primary else WakeMoveSunrise
    Canvas(
        modifier = Modifier
            .width(42.dp)
            .height(30.dp),
    ) {
        val gap = size.width / (sound.waveform.size * 2)
        sound.waveform.forEachIndexed { index, amplitude ->
            val x = gap * (index * 2 + 1)
            val halfHeight = size.height * amplitude * 0.42f
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(x, center.y - halfHeight),
                end = androidx.compose.ui.geometry.Offset(x, center.y + halfHeight),
                strokeWidth = gap.coerceAtLeast(3f),
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun VibrationPattern.chineseLabel(): String = when (this) {
    VibrationPattern.GENTLE -> "舒缓"
    VibrationPattern.DOUBLE_PULSE -> "双拍"
    VibrationPattern.STEADY -> "密集"
}

private fun VibrationIntensity.chineseLabel(): String = when (this) {
    VibrationIntensity.LIGHT -> "轻"
    VibrationIntensity.MEDIUM -> "中"
    VibrationIntensity.STRONG -> "强"
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
