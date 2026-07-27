package com.wakemove.android.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wakemove.android.domain.Alarm
import com.wakemove.android.domain.AlarmRepository
import com.wakemove.android.health.AndroidHealthService
import com.wakemove.android.health.HealthSnapshot
import com.wakemove.android.scheduling.AlarmScheduler
import com.wakemove.android.ui.alarms.AlarmEditorScreen
import com.wakemove.android.ui.alarms.AlarmEditorUiState
import com.wakemove.android.ui.alarms.AlarmEditorViewModel
import com.wakemove.android.ui.alarms.AlarmListScreen
import com.wakemove.android.ui.alarms.AlarmListViewModel
import kotlinx.coroutines.launch

interface AlarmUiDependencies {
    val alarmRepository: AlarmRepository
    val alarmScheduler: AlarmScheduler
    val healthService: AndroidHealthService
}

private enum class MainDestination(val label: String, val iconText: String) {
    ALARMS("闹钟", "钟"),
    HISTORY("历史", "史"),
    HEALTH("健康检查", "检"),
}

private sealed interface WakeMoveRoute {
    data class Main(val destination: MainDestination) : WakeMoveRoute
    data class Editor(val state: AlarmEditorUiState) : WakeMoveRoute
    data object Settings : WakeMoveRoute
}

@Composable
fun WakeMoveNavHost(
    repository: AlarmRepository,
    scheduler: AlarmScheduler,
    healthProvider: () -> HealthSnapshot,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val listViewModel = remember(repository, scheduler) {
        AlarmListViewModel(repository, scheduler)
    }
    val editorViewModel = remember(repository, scheduler, healthProvider) {
        AlarmEditorViewModel(repository, scheduler, healthProvider)
    }
    val alarms by listViewModel.alarms.collectAsState(initial = emptyList())
    var route: WakeMoveRoute by remember {
        mutableStateOf(WakeMoveRoute.Main(MainDestination.ALARMS))
    }

    when (val current = route) {
        is WakeMoveRoute.Editor -> {
            AlarmEditorRoute(
                initialState = current.state,
                onBack = { route = WakeMoveRoute.Main(MainDestination.ALARMS) },
                onSave = { state ->
                    scope.launch {
                        editorViewModel.save(state)
                        route = WakeMoveRoute.Main(MainDestination.ALARMS)
                    }
                },
                onDelete = { alarmId ->
                    scope.launch {
                        editorViewModel.delete(alarmId)
                        route = WakeMoveRoute.Main(MainDestination.ALARMS)
                    }
                },
                modifier = modifier,
            )
        }
        is WakeMoveRoute.Main -> {
            MainShell(
                destination = current.destination,
                alarms = alarms,
                onDestinationSelected = { destination ->
                    route = WakeMoveRoute.Main(destination)
                },
                onCreateAlarm = {
                    route = WakeMoveRoute.Editor(editorViewModel.newState())
                },
                onEditAlarm = { alarm ->
                    route = WakeMoveRoute.Editor(
                        AlarmEditorUiState.fromAlarm(alarm, healthProvider()),
                    )
                },
                onEnabledChange = { alarm, enabled ->
                    scope.launch { listViewModel.setEnabled(alarm, enabled) }
                },
                onOpenSettings = { route = WakeMoveRoute.Settings },
                modifier = modifier,
            )
        }
        WakeMoveRoute.Settings -> {
            PlaceholderScreen(
                title = "设置",
                onBack = { route = WakeMoveRoute.Main(MainDestination.ALARMS) },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun AlarmEditorRoute(
    initialState: AlarmEditorUiState,
    onBack: () -> Unit,
    onSave: (AlarmEditorUiState) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var state by remember(initialState.alarmId) { mutableStateOf(initialState) }
    AlarmEditorScreen(
        state = state,
        onTimeChange = { state = state.copy(timeText = it) },
        onLabelChange = { state = state.copy(label = it) },
        onDayToggle = { day ->
            state = state.copy(
                selectedDays = state.selectedDays.toMutableSet().apply {
                    if (!add(day)) remove(day)
                },
            )
        },
        onChallengeSelected = { type -> state = state.copy(challengeType = type) },
        onTargetCountChange = { count -> state = state.copy(targetCount = count) },
        onSave = { onSave(state) },
        onDelete = { state.alarmId?.let(onDelete) },
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun MainShell(
    destination: MainDestination,
    alarms: List<Alarm>,
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
