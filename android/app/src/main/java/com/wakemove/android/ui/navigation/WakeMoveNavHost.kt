package com.wakemove.android.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.History
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.wakemove.android.health.AndroidHealthService
import com.wakemove.android.health.HealthSnapshot
import com.wakemove.android.ringing.RingingSessionController
import com.wakemove.android.scheduling.AlarmScheduler
import com.wakemove.android.ui.alarms.AlarmEditorScreen
import com.wakemove.android.ui.alarms.AlarmEditorUiState
import com.wakemove.android.ui.alarms.AlarmEditorViewModel
import com.wakemove.android.ui.alarms.AlarmListScreen
import com.wakemove.android.ui.alarms.AlarmListViewModel
import com.wakemove.android.ui.health.HealthIssue
import com.wakemove.android.ui.health.HealthScreen
import com.wakemove.android.ui.health.launchHealthRepair
import com.wakemove.android.ui.history.HistoryScreen
import com.wakemove.android.ui.ringing.RingingFlowHost
import com.wakemove.android.ui.settings.SettingsScreen
import com.wakemove.android.ui.theme.WakeMoveMutedText
import com.wakemove.android.ui.theme.WakeMovePeach
import java.time.DayOfWeek
import kotlinx.coroutines.launch

interface AlarmUiDependencies {
    val alarmRepository: AlarmRepository
    val alarmScheduler: AlarmScheduler
    val healthService: AndroidHealthService
    val ringingSessionController: RingingSessionController
}

private enum class MainDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    ALARMS(ROUTE_ALARMS, "闹钟", Icons.Outlined.Alarm),
    HISTORY(ROUTE_HISTORY, "历史", Icons.Outlined.History),
    HEALTH(ROUTE_HEALTH, "健康检查", Icons.Outlined.HealthAndSafety),
}

@Composable
fun WakeMoveNavHost(
    repository: AlarmRepository,
    scheduler: AlarmScheduler,
    healthProvider: () -> HealthSnapshot,
    modifier: Modifier = Modifier,
    ringingController: RingingSessionController? = null,
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
            route = ROUTE_ALARMS
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
                onBack = { route = ROUTE_ALARMS },
                onOpenHealth = { route = ROUTE_HEALTH },
                onClearHistory = {
                    coroutineScope.launch {
                        repository.clearHistory()
                        historyVersion += 1
                    }
                },
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
                onOpenSettings = { route = ROUTE_SETTINGS },
                historyEvents = historyEvents,
                healthProvider = healthProvider,
                schedulingProvider = schedulingProvider,
                onClearHistory = {
                    coroutineScope.launch {
                        repository.clearHistory()
                        historyVersion += 1
                    }
                },
                onRepairHealth = { issue -> launchHealthRepair(context, issue) },
                modifier = modifier,
            )
        }
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
    onOpenSettings: () -> Unit,
    historyEvents: List<AlarmEvent>,
    healthProvider: () -> HealthSnapshot,
    schedulingProvider: () -> com.wakemove.android.scheduling.SchedulerHealthSnapshot,
    onClearHistory: () -> Unit,
    onRepairHealth: (HealthIssue) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
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
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (destination) {
                MainDestination.ALARMS -> AlarmListScreen(
                    alarms = alarms,
                    activeSession = activeSession,
                    operationState = operationState,
                    onCreateAlarm = onCreateAlarm,
                    onEditAlarm = onEditAlarm,
                    onEnabledChange = onEnabledChange,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.fillMaxSize(),
                )
                MainDestination.HISTORY -> HistoryScreen(
                    events = historyEvents,
                    onClearHistory = onClearHistory,
                    modifier = Modifier.fillMaxSize(),
                )
                MainDestination.HEALTH -> HealthScreen(
                    healthProvider = healthProvider,
                    schedulingProvider = schedulingProvider,
                    onRepair = onRepairHealth,
                    modifier = Modifier.fillMaxSize(),
                )
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
