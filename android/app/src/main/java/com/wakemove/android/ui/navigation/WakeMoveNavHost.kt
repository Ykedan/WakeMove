package com.wakemove.android.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wakemove.android.domain.Alarm
import com.wakemove.android.domain.AlarmRepository
import com.wakemove.android.domain.ChallengeType
import com.wakemove.android.health.AndroidHealthService
import com.wakemove.android.health.HealthSnapshot
import com.wakemove.android.scheduling.AlarmScheduler
import com.wakemove.android.ui.alarms.AlarmEditorScreen
import com.wakemove.android.ui.alarms.AlarmEditorUiState
import com.wakemove.android.ui.alarms.AlarmEditorViewModel
import com.wakemove.android.ui.alarms.AlarmListScreen
import com.wakemove.android.ui.alarms.AlarmListViewModel
import java.time.DayOfWeek

interface AlarmUiDependencies {
    val alarmRepository: AlarmRepository
    val alarmScheduler: AlarmScheduler
    val healthService: AndroidHealthService
}

private enum class MainDestination(
    val route: String,
    val label: String,
    val iconText: String,
) {
    ALARMS(ROUTE_ALARMS, "闹钟", "钟"),
    HISTORY(ROUTE_HISTORY, "历史", "史"),
    HEALTH(ROUTE_HEALTH, "健康检查", "检"),
}

@Composable
fun WakeMoveNavHost(
    repository: AlarmRepository,
    scheduler: AlarmScheduler,
    healthProvider: () -> HealthSnapshot,
    modifier: Modifier = Modifier,
) {
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
    val listOperation by listViewModel.operationState.collectAsState()
    val editorOperation by editorViewModel.operationState.collectAsState()

    var route by rememberSaveable { mutableStateOf(ROUTE_ALARMS) }
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
                onTimeChange = { editorState = editorState.copy(timeText = it) },
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
            PlaceholderScreen(
                title = "设置",
                onBack = { route = ROUTE_ALARMS },
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
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun MainShell(
    destination: MainDestination,
    alarms: List<Alarm>,
    operationState: com.wakemove.android.ui.alarms.AlarmOperationUiState,
    onDestinationSelected: (MainDestination) -> Unit,
    onCreateAlarm: () -> Unit,
    onEditAlarm: (Alarm) -> Unit,
    onEnabledChange: (Alarm, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
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
                        selected = destination == item,
                        onClick = { onDestinationSelected(item) },
                        icon = { Text(item.iconText) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSurface,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
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
                    operationState = operationState,
                    onCreateAlarm = onCreateAlarm,
                    onEditAlarm = onEditAlarm,
                    onEnabledChange = onEnabledChange,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.fillMaxSize(),
                )
                MainDestination.HISTORY -> PlaceholderContent("历史", Modifier.fillMaxSize())
                MainDestination.HEALTH -> PlaceholderContent("健康检查", Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(modifier.fillMaxSize()) {
        androidx.compose.material3.TextButton(onClick = onBack) {
            Text("返回")
        }
        PlaceholderContent(title, Modifier.weight(1f))
    }
}

@Composable
private fun PlaceholderContent(title: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text("${title}功能将在下一阶段接入")
    }
}

private fun alarmEditorStateSaver(
    healthProvider: () -> HealthSnapshot,
): Saver<AlarmEditorUiState, Any> = listSaver(
    save = { state ->
        listOf(
            state.draftId,
            state.alarmId.orEmpty(),
            state.timeText,
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
            timeText = saved[2] as String,
            label = saved[3] as String,
            selectedDays = (saved[4] as String)
                .split(',')
                .filter(String::isNotBlank)
                .mapTo(linkedSetOf()) { enumValueOf<DayOfWeek>(it) },
            challengeType = enumValueOf<ChallengeType>(saved[5] as String),
            targetCount = saved[6] as Int,
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
