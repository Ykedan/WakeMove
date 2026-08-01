package com.wakemove.android.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.History
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wakemove.android.domain.Alarm
import com.wakemove.android.domain.AlarmEvent
import com.wakemove.android.domain.AlarmRepository
import com.wakemove.android.domain.ChallengeType
import com.wakemove.android.domain.SessionStatus
import com.wakemove.android.domain.VibrationIntensity
import com.wakemove.android.domain.VibrationPattern
import com.wakemove.android.health.AndroidHealthService
import com.wakemove.android.health.HealthSnapshot
import com.wakemove.android.ringing.AlarmSoundCatalog
import com.wakemove.android.ringing.RingingSessionController
import com.wakemove.android.scheduling.AlarmScheduler
import com.wakemove.android.ui.alarms.AlarmEditorScreen
import com.wakemove.android.ui.alarms.AlarmEditorUiState
import com.wakemove.android.ui.alarms.AlarmEditorViewModel
import com.wakemove.android.ui.alarms.AlarmListScreen
import com.wakemove.android.ui.alarms.AlarmListViewModel
import com.wakemove.android.ui.health.HealthScreen
import com.wakemove.android.ui.health.launchHealthRepair
import com.wakemove.android.ui.history.HistoryScreen
import com.wakemove.android.ui.ringing.RingingFlowHost
import com.wakemove.android.ui.settings.SettingsScreen
import com.wakemove.android.ui.settings.AppUpdateDialog
import com.wakemove.android.ui.settings.WakeMoveSettings
import com.wakemove.android.ui.theme.WakeMoveMutedText
import com.wakemove.android.ui.theme.WakeMovePeach
import com.wakemove.android.update.AppUpdateManager
import com.wakemove.android.update.AppUpdateUiState
import java.time.DayOfWeek
import kotlinx.coroutines.launch

interface AlarmUiDependencies {
    val alarmRepository: AlarmRepository
    val alarmScheduler: AlarmScheduler
    val healthService: AndroidHealthService
    val ringingSessionController: RingingSessionController
    val appUpdateManager: AppUpdateManager
}

private enum class MainDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    ALARMS(ROUTE_ALARMS, "闹钟", Icons.Outlined.Alarm),
    HISTORY(ROUTE_HISTORY, "历史", Icons.Outlined.History),
}

@Composable
fun WakeMoveNavHost(
    repository: AlarmRepository,
    scheduler: AlarmScheduler,
    healthProvider: () -> HealthSnapshot,
    modifier: Modifier = Modifier,
    ringingController: RingingSessionController? = null,
    settings: WakeMoveSettings = WakeMoveSettings(),
    onSettingsChange: (WakeMoveSettings) -> Unit = {},
    updateManager: AppUpdateManager? = null,
) {
    val ringingState = ringingController?.state?.collectAsState()?.value
    if (ringingController != null &&
        ringingState?.session?.status == SessionStatus.RINGING
    ) {
        RingingFlowHost(ringingController, healthProvider, modifier)
        return
    }
    val listFactory = remember(repository, scheduler, healthProvider) {
        WakeMoveViewModelFactory {
            AlarmListViewModel(repository, scheduler, healthProvider)
        }
    }
    val editorFactory = remember(repository, scheduler, healthProvider) {
        WakeMoveViewModelFactory {
            AlarmEditorViewModel(repository, scheduler, healthProvider)
        }
    }
    val listViewModel: AlarmListViewModel = viewModel(
        key = "alarm-list",
        factory = listFactory,
    )
    val editorViewModel: AlarmEditorViewModel = viewModel(
        key = "alarm-editor",
        factory = editorFactory,
    )
    val alarms by listViewModel.alarms.collectAsState(initial = emptyList())
    val activeSession by listViewModel.activeSession.collectAsState(initial = null)
    val listOperation by listViewModel.operationState.collectAsState()
    val editorOperation by editorViewModel.operationState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val updateState = updateManager?.state?.collectAsState()?.value ?: AppUpdateUiState()
    val context = LocalContext.current
    val schedulingProvider = remember(scheduler) { { scheduler.healthSnapshot() } }

    var route by rememberSaveable { mutableStateOf(ROUTE_ALARMS) }
    var historyVersion by rememberSaveable { mutableIntStateOf(0) }
    val historyEvents by produceState<List<AlarmEvent>>(
        initialValue = emptyList(),
        key1 = route,
        key2 = historyVersion,
    ) {
        if (route == ROUTE_HISTORY) {
            value = runCatching { repository.recentEvents() }.getOrDefault(emptyList())
        }
    }
    val stateSaver = remember(healthProvider) { alarmEditorStateSaver(healthProvider) }
    var editorState by rememberSaveable(stateSaver = stateSaver) {
        mutableStateOf(editorViewModel.newState())
    }

    BackHandler(enabled = route != ROUTE_ALARMS) {
        if (!editorOperation.isInFlight) {
            route = if (route == ROUTE_HEALTH) ROUTE_SETTINGS else ROUTE_ALARMS
        }
    }

    when (route) {
        ROUTE_EDITOR -> {
            AlarmEditorScreen(
                state = editorState,
                operationState = editorOperation,
                onTimeChange = { hour, minute ->
                    editorState = editorState.copy(hour = hour, minute = minute)
                },
                onLabelChange = { editorState = editorState.copy(label = it) },
                onDayToggle = { day ->
                    editorState = editorState.copy(
                        selectedDays = editorState.selectedDays.toMutableSet().apply {
                            if (!add(day)) remove(day)
                        },
                    )
                },
                onSoundSelected = { soundId ->
                    editorState = editorState.copy(soundId = soundId)
                },
                onVibrationEnabledChange = { enabled ->
                    editorState = editorState.copy(vibrationEnabled = enabled)
                },
                onVibrationPatternSelected = { pattern ->
                    editorState = editorState.copy(vibrationPattern = pattern)
                },
                onVibrationIntensitySelected = { intensity ->
                    editorState = editorState.copy(vibrationIntensity = intensity)
                },
                onChallengeSelected = { type ->
                    editorState = editorState.copy(challengeType = type)
                },
                onTargetCountChange = { count ->
                    editorState = editorState.copy(targetCount = count)
                },
                onSave = {
                    editorViewModel.submit(editorState) {
                        route = ROUTE_ALARMS
                    }
                },
                onDelete = {
                    editorState.alarmId?.let { alarmId ->
                        editorViewModel.submitDelete(alarmId) {
                            route = ROUTE_ALARMS
                        }
                    }
                },
                onBack = {
                    if (!editorOperation.isInFlight) {
                        route = ROUTE_ALARMS
                    }
                },
                navigationEnabled = !editorOperation.isInFlight,
                modifier = modifier,
            )
        }
        ROUTE_SETTINGS -> {
            SettingsScreen(
                settings = settings,
                onSettingsChange = onSettingsChange,
                onBack = { route = ROUTE_ALARMS },
                onOpenHealth = { route = ROUTE_HEALTH },
                onClearHistory = {
                    coroutineScope.launch {
                        repository.clearHistory()
                        historyVersion += 1
                    }
                },
                updateState = updateState,
                onCheckUpdate = { updateManager?.showAvailableUpdate() },
                modifier = modifier,
            )
        }
        ROUTE_HEALTH -> {
            HealthScreen(
                healthProvider = healthProvider,
                schedulingProvider = schedulingProvider,
                onRepair = { issue -> launchHealthRepair(context, issue) },
                onBack = { route = ROUTE_SETTINGS },
                modifier = modifier,
            )
        }
        else -> {
            val destination = MainDestination.entries.firstOrNull {
                it.route == route
            } ?: MainDestination.ALARMS
            MainShell(
                destination = destination,
                alarms = alarms,
                activeSession = activeSession,
                operationState = listOperation,
                onDestinationSelected = { selected -> route = selected.route },
                onCreateAlarm = {
                    editorState = editorViewModel.newState()
                    route = ROUTE_EDITOR
                },
                onEditAlarm = { alarm ->
                    editorState = AlarmEditorUiState.fromAlarm(alarm, healthProvider())
                    route = ROUTE_EDITOR
                },
                onEnabledChange = listViewModel::submitEnabledChange,
                onChallengeNow = {
                    coroutineScope.launch {
                        ringingController?.challengeNow()
                    }
                },
                onOpenSettings = { route = ROUTE_SETTINGS },
                historyEvents = historyEvents,
                onClearHistory = {
                    coroutineScope.launch {
                        repository.clearHistory()
                        historyVersion += 1
                    }
                },
                modifier = modifier,
            )
        }
    }
    if (updateManager != null) {
        AppUpdateDialog(
            state = updateState,
            onDismiss = updateManager::dismissDialog,
            onDownload = updateManager::downloadUpdate,
            onInstall = updateManager::installDownloadedUpdate,
            onRetry = { updateManager.checkForUpdate(manual = true) },
            onIgnoreVersion = updateManager::ignoreCurrentVersion,
        )
    }
}

@Composable
private fun MainShell(
    destination: MainDestination,
    alarms: List<Alarm>,
    activeSession: com.wakemove.android.domain.RingingSession?,
    operationState: com.wakemove.android.ui.alarms.AlarmOperationUiState,
    onDestinationSelected: (MainDestination) -> Unit,
    onCreateAlarm: () -> Unit,
    onEditAlarm: (Alarm) -> Unit,
    onEnabledChange: (Alarm, Boolean) -> Unit,
    onChallengeNow: (com.wakemove.android.domain.RingingSession) -> Unit,
    onOpenSettings: () -> Unit,
    historyEvents: List<AlarmEvent>,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                NavigationBar(
                    modifier = Modifier.clip(MaterialTheme.shapes.extraLarge),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    MainDestination.entries.forEach { item ->
                        NavigationBarItem(
                            modifier = Modifier.semantics {
                                contentDescription = item.label
                            },
                            selected = destination == item,
                            onClick = { onDestinationSelected(item) },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                )
                            },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = WakeMoveMutedText,
                                unselectedTextColor = WakeMoveMutedText,
                                indicatorColor = WakeMovePeach,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AnimatedContent(
                targetState = destination,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    fadeIn(tween(durationMillis = 220)) togetherWith
                        fadeOut(tween(durationMillis = 160))
                },
                label = "main-destination",
            ) { visibleDestination ->
                when (visibleDestination) {
                    MainDestination.ALARMS -> AlarmListScreen(
                        alarms = alarms,
                        activeSession = activeSession,
                        operationState = operationState,
                        onCreateAlarm = onCreateAlarm,
                        onEditAlarm = onEditAlarm,
                        onEnabledChange = onEnabledChange,
                        onChallengeNow = onChallengeNow,
                        onOpenSettings = onOpenSettings,
                        modifier = Modifier.fillMaxSize(),
                    )
                    MainDestination.HISTORY -> HistoryScreen(
                        events = historyEvents,
                        onClearHistory = onClearHistory,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

private fun alarmEditorStateSaver(
    healthProvider: () -> HealthSnapshot,
): Saver<AlarmEditorUiState, Any> = listSaver(
    save = { state ->
        listOf(
            state.draftId,
            state.alarmId.orEmpty(),
            state.hour,
            state.minute,
            state.label,
            state.selectedDays.joinToString(",") { it.name },
            state.challengeType.name,
            state.targetCount,
            state.soundId,
            state.vibrationEnabled,
            state.vibrationPattern.name,
            state.vibrationIntensity.name,
        )
    },
    restore = { saved ->
        AlarmEditorUiState(
            draftId = saved[0] as String,
            alarmId = (saved[1] as String).ifBlank { null },
            hour = saved[2] as Int,
            minute = saved[3] as Int,
            label = saved[4] as String,
            selectedDays = (saved[5] as String)
                .split(',')
                .filter(String::isNotBlank)
                .mapTo(linkedSetOf()) { enumValueOf<DayOfWeek>(it) },
            challengeType = enumValueOf<ChallengeType>(saved[6] as String),
            targetCount = saved[7] as Int,
            soundId = saved.getOrNull(8) as? String ?: AlarmSoundCatalog.DEFAULT_ID,
            vibrationEnabled = saved.getOrNull(9) as? Boolean ?: true,
            vibrationPattern = (saved.getOrNull(10) as? String)
                ?.let { enumValueOf<VibrationPattern>(it) }
                ?: VibrationPattern.GENTLE,
            vibrationIntensity = (saved.getOrNull(11) as? String)
                ?.let { enumValueOf<VibrationIntensity>(it) }
                ?: VibrationIntensity.MEDIUM,
            health = healthProvider(),
        )
    },
)

private class WakeMoveViewModelFactory<T : ViewModel>(
    private val createViewModel: () -> T,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM =
        createViewModel() as VM
}

private const val ROUTE_ALARMS = "alarms"
private const val ROUTE_EDITOR = "editor"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_HEALTH = "health"
private const val ROUTE_SETTINGS = "settings"
