package com.wakemove.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import com.wakemove.android.MainActivity
import com.wakemove.android.domain.Alarm
import com.wakemove.android.domain.AlarmEvent
import com.wakemove.android.domain.AlarmRepository
import com.wakemove.android.domain.ChallengeType
import com.wakemove.android.domain.PendingAlarmSchedule
import com.wakemove.android.domain.RingingSession
import com.wakemove.android.domain.SessionStatus
import com.wakemove.android.health.HealthSnapshot
import com.wakemove.android.health.HealthStatus
import com.wakemove.android.scheduling.AlarmScheduler
import com.wakemove.android.ui.alarms.AlarmEditorScreen
import com.wakemove.android.ui.alarms.AlarmEditorUiState
import com.wakemove.android.ui.alarms.AlarmEditorViewModel
import com.wakemove.android.ui.alarms.AlarmListScreen
import com.wakemove.android.ui.alarms.AlarmListViewModel
import com.wakemove.android.ui.alarms.AlarmOperationUiState
import com.wakemove.android.ui.theme.WakeMoveTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AlarmEditorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mainActivityDoesNotExposeLegacyActionBar() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNull(activity.actionBar)
            }
        }
    }

    @Test
    fun timeIsRequiredAndSaveRequiresReadyHealth() {
        var state by mutableStateOf(
            AlarmEditorUiState(
                timeText = "",
                health = readyHealth,
            ),
        )

        composeRule.setContent {
            WakeMoveTheme {
                AlarmEditorScreen(
                    state = state,
                    onTimeChange = { state = state.copy(timeText = it) },
                    onDayToggle = {},
                    onChallengeSelected = {},
                    onTargetCountChange = {},
                    onSave = {},
                    onDelete = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("请选择有效时间").assertIsDisplayed()
        composeRule.onNodeWithTag("save_alarm").assertIsNotEnabled()
        composeRule.onNodeWithTag("alarm_time").performTextReplacement("07:30")
        composeRule.onNodeWithTag("save_alarm").assertIsEnabled()

        composeRule.runOnIdle {
            state = state.copy(
                health = readyHealth.copy(exactAlarm = HealthStatus.ACTION_REQUIRED),
            )
        }
        composeRule.onNodeWithTag("save_alarm").assertIsNotEnabled()
        composeRule.onNodeWithText("请先完成健康检查").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun weekdayTogglesExposeSelectedState() {
        var state by mutableStateOf(
            AlarmEditorUiState(
                timeText = "07:30",
                health = readyHealth,
            ),
        )
        composeRule.setContent {
            WakeMoveTheme {
                AlarmEditorScreen(
                    state = state,
                    onTimeChange = {},
                    onDayToggle = { day ->
                        state = state.copy(
                            selectedDays = state.selectedDays.toMutableSet().apply {
                                if (!add(day)) remove(day)
                            },
                        )
                    },
                    onChallengeSelected = {},
                    onTargetCountChange = {},
                    onSave = {},
                    onDelete = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("weekday_MONDAY").performClick()
        composeRule.onNodeWithTag("weekday_MONDAY").assertIsSelected()
        composeRule.onNodeWithTag("weekday_MONDAY").performClick()
        composeRule.onNodeWithTag("weekday_MONDAY").assertIsNotSelected()
    }

    @Test
    fun voiceChallengeHidesTargetCountWithoutBlockingSaveBeforeChallengePermission() {
        var state by mutableStateOf(
            AlarmEditorUiState(
                timeText = "07:30",
                health = readyHealth,
            ),
        )
        composeRule.setContent {
            WakeMoveTheme {
                AlarmEditorScreen(
                    state = state,
                    onTimeChange = {},
                    onDayToggle = {},
                    onChallengeSelected = { state = state.copy(challengeType = it) },
                    onTargetCountChange = { state = state.copy(targetCount = it) },
                    onSave = {},
                    onDelete = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("target_count").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("challenge_VOICE_PHRASE").performClick()
        composeRule.onNodeWithTag("challenge_VOICE_PHRASE").assertIsSelected()
        composeRule.onNodeWithTag("target_count").assertDoesNotExist()

        composeRule.runOnIdle {
            state = state.copy(
                health = readyHealth.copy(microphone = HealthStatus.ACTION_REQUIRED),
            )
        }
        composeRule.onNodeWithTag("save_alarm").assertIsEnabled()
        composeRule.onNodeWithText("语音短语需要麦克风权限").assertDoesNotExist()
    }

    @Test
    fun existingAlarmRequiresDeleteConfirmation() {
        var deleted = false
        composeRule.setContent {
            WakeMoveTheme {
                AlarmEditorScreen(
                    state = AlarmEditorUiState(
                        alarmId = "alarm-1",
                        timeText = "07:30",
                        health = readyHealth,
                    ),
                    onTimeChange = {},
                    onDayToggle = {},
                    onChallengeSelected = {},
                    onTargetCountChange = {},
                    onSave = {},
                    onDelete = { deleted = true },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("删除闹钟").performScrollTo().performClick()
        composeRule.onNodeWithText("确认删除？").assertIsDisplayed()
        composeRule.runOnIdle { assertFalse(deleted) }
        composeRule.onNodeWithTag("confirm_delete").performClick()
        composeRule.runOnIdle { assertTrue(deleted) }
    }

    @Test
    fun listExposesCreateEditAndEnableActions() {
        val alarm = alarm()
        var created = false
        var editedId: String? = null
        var enabledChange: Boolean? = null
        composeRule.setContent {
            WakeMoveTheme {
                AlarmListScreen(
                    alarms = listOf(alarm),
                    onCreateAlarm = { created = true },
                    onEditAlarm = { editedId = it.id },
                    onEnabledChange = { _, enabled -> enabledChange = enabled },
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag("add_alarm").performClick()
        composeRule.runOnIdle { assertTrue(created) }
        composeRule.onNodeWithTag("alarm_card_alarm-1").performClick()
        composeRule.runOnIdle { assertEquals("alarm-1", editedId) }
        composeRule.onNodeWithTag("alarm_enabled_alarm-1").performClick()
        composeRule.runOnIdle { assertEquals(false, enabledChange) }
    }

    @Test
    fun emptyAlarmListShowsSunriseCallToAction() {
        composeRule.setContent {
            WakeMoveTheme {
                AlarmListScreen(
                    alarms = emptyList(),
                    onCreateAlarm = {},
                    onEditAlarm = {},
                    onEnabledChange = { _, _ -> },
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag("empty_alarm_state").assertIsDisplayed()
        composeRule.onNodeWithText("还没有闹钟").assertIsDisplayed()
        composeRule.onNodeWithText("设置第一个闹钟").assertIsDisplayed()
        composeRule.onNodeWithTag("next_alarm_card").assertDoesNotExist()
    }

    @Test
    fun enabledAlarmListShowsDeterministicNextAlarm() {
        composeRule.setContent {
            WakeMoveTheme {
                AlarmListScreen(
                    alarms = listOf(alarm()),
                    onCreateAlarm = {},
                    onEditAlarm = {},
                    onEnabledChange = { _, _ -> },
                    onOpenSettings = {},
                    nowProvider = { MONDAY_MORNING },
                )
            }
        }

        composeRule.onNodeWithTag("next_alarm_card").assertIsDisplayed()
        composeRule.onNodeWithText("下一次唤醒").assertIsDisplayed()
        composeRule.onNodeWithText("07:30").assertIsDisplayed()
    }

    @Test
    fun allDisabledAlarmListInvitesEnablingAnAlarm() {
        composeRule.setContent {
            WakeMoveTheme {
                AlarmListScreen(
                    alarms = listOf(alarm().copy(enabled = false)),
                    onCreateAlarm = {},
                    onEditAlarm = {},
                    onEnabledChange = { _, _ -> },
                    onOpenSettings = {},
                    nowProvider = { MONDAY_MORNING },
                )
            }
        }

        composeRule.onNodeWithText("开启一个闹钟，迎接新的早晨").assertIsDisplayed()
        composeRule.onNodeWithTag("next_alarm_card").assertDoesNotExist()
    }

    @Test
    fun narrowAlarmListKeepsSettingsVisible() {
        composeRule.setContent {
            WakeMoveTheme {
                Box(Modifier.width(320.dp)) {
                    AlarmListScreen(
                        alarms = emptyList(),
                        onCreateAlarm = {},
                        onEditAlarm = {},
                        onEnabledChange = { _, _ -> },
                        onOpenSettings = {},
                    )
                }
            }
        }

        val settingsBounds = composeRule.onNodeWithTag("settings_button")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .getUnclippedBoundsInRoot()
        assertTrue(settingsBounds.right <= 320.dp)
    }

    @Test
    fun inFlightAlarmListDisablesNextAlarmHeroSemantics() {
        composeRule.setContent {
            WakeMoveTheme {
                AlarmListScreen(
                    alarms = listOf(alarm()),
                    operationState = AlarmOperationUiState(isInFlight = true),
                    onCreateAlarm = {},
                    onEditAlarm = {},
                    onEnabledChange = { _, _ -> },
                    onOpenSettings = {},
                    nowProvider = { MONDAY_MORNING },
                )
            }
        }

        composeRule.onNodeWithTag("next_alarm_card").assertIsNotEnabled()
    }

    @Test
    fun viewModelsCreateUpdateEnableDisableAndDeleteThroughContracts() = runBlocking {
        val repository = FakeAlarmRepository()
        val scheduler = FakeAlarmScheduler()
        val editor = AlarmEditorViewModel(
            repository = repository,
            scheduler = scheduler,
            healthProvider = { readyHealth },
            instantProvider = { Instant.parse("2026-07-27T01:00:00Z") },
            idProvider = { "new-alarm" },
        )
        val list = AlarmListViewModel(repository, scheduler, { readyHealth })

        val created = editor.save(
            AlarmEditorUiState(
                timeText = "07:30",
                selectedDays = setOf(DayOfWeek.MONDAY),
                challengeType = ChallengeType.SQUAT,
                targetCount = 12,
                health = readyHealth,
            ),
        )
        assertEquals("new-alarm", created.id)
        assertEquals(created, repository.getAlarm("new-alarm"))
        assertEquals(1, scheduler.rescheduleCount)

        val updated = editor.save(
            AlarmEditorUiState.fromAlarm(
                created.copy(label = "晨跑"),
                readyHealth,
            ).copy(timeText = "08:15"),
        )
        assertEquals(LocalTime.of(8, 15), updated.time)
        assertEquals(created.createdAt, updated.createdAt)
        assertEquals(2, scheduler.rescheduleCount)

        list.setEnabled(updated, false)
        assertFalse(checkNotNull(repository.getAlarm(updated.id)).enabled)
        list.setEnabled(updated, true)
        assertTrue(checkNotNull(repository.getAlarm(updated.id)).enabled)

        editor.delete(updated.id)
        assertNull(repository.getAlarm(updated.id))
        assertEquals(listOf(updated.id), scheduler.cancelledIds)
        assertEquals(5, scheduler.rescheduleCount)
    }

    @Test
    fun saveRechecksHealthImmediatelyBeforeMutation() = runBlocking {
        val repository = FakeAlarmRepository()
        val scheduler = FakeAlarmScheduler()
        val blockedHealth = readyHealth.copy(exactAlarm = HealthStatus.ACTION_REQUIRED)
        val editor = AlarmEditorViewModel(
            repository = repository,
            scheduler = scheduler,
            healthProvider = { blockedHealth },
            idProvider = { "health-blocked" },
        )

        val result = runCatching {
            editor.save(
                AlarmEditorUiState(
                    timeText = "07:30",
                    selectedDays = setOf(DayOfWeek.MONDAY),
                    health = readyHealth,
                ),
            )
        }

        assertTrue(result.isFailure)
        assertNull(repository.getAlarm("health-blocked"))
        assertEquals(0, scheduler.rescheduleCount)
    }

    @Test
    fun failedCreateSchedulingRemovesPersistedAlarm() = runBlocking {
        val repository = FakeAlarmRepository()
        val scheduler = FakeAlarmScheduler(failuresRemaining = 1)
        val editor = AlarmEditorViewModel(
            repository = repository,
            scheduler = scheduler,
            healthProvider = { readyHealth },
            idProvider = { "failed-create" },
        )

        val result = runCatching {
            editor.save(
                AlarmEditorUiState(
                    timeText = "07:30",
                    selectedDays = setOf(DayOfWeek.MONDAY),
                    health = readyHealth,
                ),
            )
        }

        assertTrue(result.isFailure)
        assertNull(repository.getAlarm("failed-create"))
        assertEquals(listOf("failed-create"), scheduler.cancelledIds)
    }

    @Test
    fun failedUpdateSchedulingRestoresPreviousAlarm() = runBlocking {
        val repository = FakeAlarmRepository()
        val previous = alarm()
        repository.upsertAlarm(previous)
        val scheduler = FakeAlarmScheduler(failuresRemaining = 1)
        val editor = AlarmEditorViewModel(
            repository = repository,
            scheduler = scheduler,
            healthProvider = { readyHealth },
        )

        val result = runCatching {
            editor.save(
                AlarmEditorUiState.fromAlarm(previous, readyHealth)
                    .copy(timeText = "09:45"),
            )
        }

        assertTrue(result.isFailure)
        assertEquals(previous, repository.getAlarm(previous.id))
    }

    @Test
    fun failedEnableAndDisableRestorePreviousVisibleState() = runBlocking {
        val repository = FakeAlarmRepository()
        val scheduler = FakeAlarmScheduler()
        val disabled = alarm().copy(enabled = false)
        repository.upsertAlarm(disabled)
        val list = AlarmListViewModel(repository, scheduler, { readyHealth })

        scheduler.failuresRemaining = 1
        assertTrue(runCatching { list.setEnabled(disabled, true) }.isFailure)
        assertFalse(checkNotNull(repository.getAlarm(disabled.id)).enabled)

        val enabled = disabled.copy(enabled = true)
        repository.upsertAlarm(enabled)
        scheduler.failuresRemaining = 1
        assertTrue(runCatching { list.setEnabled(enabled, false) }.isFailure)
        assertTrue(checkNotNull(repository.getAlarm(enabled.id)).enabled)
    }

    @Test
    fun failedRollbackReconciliationForcesAlarmDisabledAndCancelled() = runBlocking {
        val repository = FakeAlarmRepository()
        val enabled = alarm()
        repository.upsertAlarm(enabled)
        val scheduler = FakeAlarmScheduler(failuresRemaining = 2)
        val list = AlarmListViewModel(repository, scheduler, { readyHealth })

        val result = runCatching { list.setEnabled(enabled, false) }

        assertTrue(result.isFailure)
        assertFalse(checkNotNull(repository.getAlarm(enabled.id)).enabled)
        assertEquals(listOf(enabled.id), scheduler.cancelledIds)
    }

    @Test
    fun concurrentCreateSubmissionMutatesAndSchedulesOnlyOnce() = runBlocking {
        val repository = FakeAlarmRepository()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val scheduler = FakeAlarmScheduler(
            onReschedule = {
                entered.complete(Unit)
                release.await()
            },
        )
        var nextId = 0
        val editor = AlarmEditorViewModel(
            repository = repository,
            scheduler = scheduler,
            healthProvider = { readyHealth },
            idProvider = { "create-${++nextId}" },
        )
        val state = AlarmEditorUiState(
            timeText = "07:30",
            selectedDays = setOf(DayOfWeek.MONDAY),
            health = readyHealth,
        )

        val first = async { editor.save(state) }
        entered.await()
        val second = async { editor.save(state) }
        release.complete(Unit)
        first.await()
        second.await()

        assertEquals(1, repository.alarmCount)
        assertEquals(1, scheduler.rescheduleCount)
    }

    private class FakeAlarmRepository : AlarmRepository {
        private val alarms = linkedMapOf<String, Alarm>()
        private val alarmFlow = MutableStateFlow<List<Alarm>>(emptyList())

        override fun observeAlarms(): Flow<List<Alarm>> = alarmFlow

        override suspend fun upsertAlarm(alarm: Alarm) {
            alarms[alarm.id] = alarm
            alarmFlow.value = alarms.values.sortedBy(Alarm::time)
        }

        override suspend fun deleteAlarm(id: String) {
            alarms.remove(id)
            alarmFlow.value = alarms.values.sortedBy(Alarm::time)
        }

        override suspend fun getAlarm(id: String): Alarm? = alarms[id]
        override suspend fun saveSession(session: RingingSession) = Unit
        override suspend fun activeSession(): RingingSession? = null
        override suspend fun transitionSession(
            session: RingingSession,
            expectedStatuses: Set<SessionStatus>,
            event: AlarmEvent?,
            alarmUpdate: Alarm?,
        ): Boolean = false

        override suspend fun pendingSchedules(): List<PendingAlarmSchedule> = emptyList()
        override suspend fun acknowledgePendingSchedule(
            sessionId: String,
            scheduledAt: Instant,
        ): Boolean = false

        override suspend fun appendEvent(event: AlarmEvent) = Unit
        override suspend fun recentEvents(limit: Int): List<AlarmEvent> = emptyList()
        override suspend fun clearHistory() = Unit

        val alarmCount: Int
            get() = alarms.size
    }

    private class FakeAlarmScheduler(
        var failuresRemaining: Int = 0,
        private val onReschedule: suspend () -> Unit = {},
    ) : AlarmScheduler {
        var rescheduleCount = 0
        val cancelledIds = mutableListOf<String>()

        override fun schedule(alarm: Alarm, at: Instant) = Unit

        override fun cancel(alarmId: String) {
            cancelledIds += alarmId
        }

        override suspend fun rescheduleAll() {
            rescheduleCount += 1
            onReschedule()
            if (failuresRemaining > 0) {
                failuresRemaining -= 1
                throw IllegalStateException("scheduler failure")
            }
        }
    }

    companion object {
        private val MONDAY_MORNING = ZonedDateTime.of(
            2026,
            7,
            27,
            6,
            0,
            0,
            0,
            ZoneId.of("Asia/Shanghai"),
        )

        private val readyHealth = HealthSnapshot(
            exactAlarm = HealthStatus.READY,
            notifications = HealthStatus.READY,
            fullScreenIntent = HealthStatus.READY,
            camera = HealthStatus.READY,
            microphone = HealthStatus.READY,
        )

        private fun alarm() = Alarm(
            id = "alarm-1",
            time = LocalTime.of(7, 30),
            label = "起床",
            enabled = true,
            repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
            soundId = "default",
            vibrationEnabled = true,
            challengeType = ChallengeType.SQUAT,
            targetCount = 12,
            createdAt = Instant.parse("2026-07-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-07-01T00:00:00Z"),
        )
    }
}
