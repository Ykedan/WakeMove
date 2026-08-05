package com.wakemove.android.ui.alarms

import com.wakemove.android.i18n.tr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wakemove.android.domain.ChallengeType
import com.wakemove.android.domain.VibrationIntensity
import com.wakemove.android.domain.VibrationPattern
import com.wakemove.android.ringing.AndroidAlarmSoundPreviewPlayer
import com.wakemove.android.ringing.AndroidAlarmVibrator
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorScreen(
    state: AlarmEditorUiState,
    modifier: Modifier = Modifier,
    operationState: AlarmOperationUiState = AlarmOperationUiState(),
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    onDayToggle: (DayOfWeek) -> Unit,
    onChallengeSelected: (ChallengeType) -> Unit,
    onTargetCountChange: (Int) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    navigationEnabled: Boolean = true,
    onLabelChange: (String) -> Unit = {},
    onSoundSelected: (String) -> Unit = {},
    onVibrationEnabledChange: (Boolean) -> Unit = {},
    onVibrationPatternSelected: (VibrationPattern) -> Unit = {},
    onVibrationIntensitySelected: (VibrationIntensity) -> Unit = {},
) {
    val context = LocalContext.current
    val soundPreview = remember(context) { AndroidAlarmSoundPreviewPlayer(context) }
    val vibrationPreview = remember(context) { AndroidAlarmVibrator(context) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showSoundPicker by remember { mutableStateOf(false) }
    var previewingSoundId by remember { mutableStateOf<String?>(null) }
    DisposableEffect(soundPreview, vibrationPreview) {
        onDispose {
            soundPreview.close()
            vibrationPreview.stop()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(if (state.alarmId == null) tr("新建闹钟") else tr("编辑闹钟"))
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        enabled = navigationEnabled,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = tr("返回"),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
            ) {
                Button(
                    onClick = onSave,
                    enabled = state.canSave && !operationState.isInFlight,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
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
                        Text(tr("保存中…"))
                    } else {
                        Text(tr("保存闹钟"))
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SunriseTimeCard(
                hour = state.hour,
                minute = state.minute,
                nextOccurrenceLabel = state.nextOccurrenceLabel,
                onTimeChange = onTimeChange,
            )

            OutlinedTextField(
                value = state.label,
                onValueChange = onLabelChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(tr("标签（可选）")) },
                placeholder = { Text(tr("例如：上班、晨跑、早课")) },
                singleLine = true,
            )

            EditorCard(title = tr("重复")) {
                WeekdaySelector(
                    selectedDays = state.selectedDays,
                    onDayToggle = onDayToggle,
                )
                Text(
                    text = if (state.selectedDays.isEmpty()) tr("仅响一次") else tr("按所选日期重复"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SoundAndVibrationCard(
                soundId = state.soundId,
                vibrationEnabled = state.vibrationEnabled,
                vibrationPattern = state.vibrationPattern,
                vibrationIntensity = state.vibrationIntensity,
                onOpenSoundPicker = { showSoundPicker = true },
                onVibrationEnabledChange = { enabled ->
                    if (!enabled) vibrationPreview.stop()
                    onVibrationEnabledChange(enabled)
                },
                onVibrationPatternSelected = onVibrationPatternSelected,
                onVibrationIntensitySelected = onVibrationIntensitySelected,
                onPreviewVibration = vibrationPreview::preview,
            )

            EditorCard(title = tr("起床挑战")) {
                ChallengeSelector(
                    selectedChallenge = state.challengeType,
                    onChallengeSelected = onChallengeSelected,
                )
            }

            if (state.challengeType != ChallengeType.VOICE_PHRASE) {
                EditorCard(title = tr("完成目标")) {
                    TargetStepper(
                        count = state.targetCount,
                        onCountChange = onTargetCountChange,
                    )
                }
            }

            state.healthMessage?.let { message ->
                EditorAlertCard(message)
            }
            operationState.errorMessage?.let { message ->
                EditorAlertCard(message)
            }

            if (state.alarmId != null) {
                OutlinedButton(
                    onClick = { showDeleteConfirmation = true },
                    enabled = !operationState.isInFlight,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(tr("删除闹钟"), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showSoundPicker) {
        SoundSelectionDialog(
            selectedSoundId = state.soundId,
            previewingSoundId = previewingSoundId,
            onSelect = onSoundSelected,
            onPreview = { soundId ->
                val playing = soundPreview.toggle(soundId) {
                    if (previewingSoundId == soundId) {
                        previewingSoundId = null
                    }
                }
                previewingSoundId = soundId.takeIf { playing }
            },
            onDismiss = {
                soundPreview.stop()
                previewingSoundId = null
                showSoundPicker = false
            },
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(tr("确认删除？")) },
            text = { Text(tr("删除后将无法恢复此闹钟。")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                    modifier = Modifier.testTag("confirm_delete"),
                ) {
                    Text(tr("删除"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(tr("取消"))
                }
            },
        )
    }
}
