package com.wakemove.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import com.wakemove.android.domain.Alarm
import com.wakemove.android.domain.AlarmEvent
import com.wakemove.android.domain.AlarmRepository
import com.wakemove.android.domain.PendingAlarmSchedule
import com.wakemove.android.domain.RingingSession
import com.wakemove.android.domain.SessionStatus
import com.wakemove.android.health.HealthSnapshot
import com.wakemove.android.health.HealthStatus
import com.wakemove.android.scheduling.AlarmScheduler
import com.wakemove.android.ui.alarms.AlarmEditorScreen
import com.wakemove.android.ui.alarms.AlarmEditorUiState
import com.wakemove.android.ui.navigation.WakeMoveNavHost
import com.wakemove.android.ui.theme.WakeMoveTheme
import java.time.DayOfWeek
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AlarmNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mainNavigationIconsExposeDestinationContentDescriptions() {
        setNavContent(FakeAlarmRepository(), FakeAlarmScheduler())
        composeRule.onNodeWithContentDescription("闹钟").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("历史").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("健康检查").assertIsDisplayed()
    }

    @Test
    fun schedulingFailureStaysInEditorAndShowsRecoverableError() {
        val repository = FakeAlarmRepository()
        val scheduler = FakeAlarmScheduler(failuresRemaining = 1)
        setNavContent(repository, scheduler)

        composeRule.onNodeWithTag("add_alarm").performClick()
        composeRule.onNodeWithTag("weekday_MONDAY").performScrollTo().performClick()
        composeRule.onNodeWithTag("save_alarm").performClick()

        composeRule.onNodeWithText("保存失败，闹钟状态已恢复")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("alarm_time_wheels").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, repository.alarmCount) }
    }

    @Test
    fun submissionShowsProgressAndPreventsSecondMutation() {
        val repository = FakeAlarmRepository()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val scheduler = FakeAlarmScheduler(
            onReschedule = {
                entered.complete(Unit)
                release.await()
            },
        )
        setNavContent(repository, scheduler)

        composeRule.onNodeWithTag("add_alarm").performClick()
        composeRule.onNodeWithTag("weekday_MONDAY").performScrollTo().performClick()
        composeRule.onNodeWithTag("save_alarm").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("submission_progress")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("save_alarm").assertIsNotEnabled()

        release.complete(Unit)
        composeRule.waitUntil(timeoutMillis = 5_000) { repository.alarmCount == 1 }
        composeRule.runOnIdle { assertEquals(1, scheduler.rescheduleCount) }
    }

    @Test
    fun systemBackIsConsumedWhileSubmissionIsInFlight() {
        val repository = FakeAlarmRepository()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val scheduler = FakeAlarmScheduler(
            onReschedule = {
                entered.complete(Unit)
                release.await()
            },
        )
        setNavContent(repository, scheduler)

        composeRule.onNodeWithTag("add_alarm").performClick()
        composeRule.onNodeWithTag("weekday_MONDAY").performScrollTo().performClick()
        composeRule.onNodeWithTag("save_alarm").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("submission_progress")
                .fetchSemanticsNodes().isNotEmpty()
        }

        Espresso.pressBack()

        composeRule.onNodeWithTag("alarm_time_wheels").assertIsDisplayed()
        composeRule.onNodeWithTag("submission_progress").assertIsDisplayed()
        release.complete(Unit)
        composeRule.waitUntil(timeoutMillis = 5_000) { repository.alarmCount == 1 }
    }

    @Test
    fun systemBackReturnsFromEditorToAlarmList() {
        val repository = FakeAlarmRepository()
        setNavContent(repository, FakeAlarmScheduler())

        composeRule.onNodeWithTag("add_alarm").performClick()
        composeRule.onNodeWithTag("alarm_time_wheels").assertIsDisplayed()
        Espresso.pressBack()

        composeRule.onNodeWithText("还没有闹钟").assertIsDisplayed()
    }

    @Test
    fun allWeekdaysRemainVisibleAndUsableAtNarrowWidth() {
        var state by mutableStateOf(
            AlarmEditorUiState(hour = 7, minute = 30, health = readyHealth),
        )
        composeRule.setContent {
            WakeMoveTheme {
                Box(Modifier.width(320.dp)) {
                    AlarmEditorScreen(
                        state = state,
                        onTimeChange = { _, _ -> },
                        onDayToggle = { day ->
                            state = state.copy(selectedDays = state.selectedDays + day)
                        },
                        onChallengeSelected = {},
                        onTargetCountChange = {},
                        onSave = {},
                        onDelete = {},
                        onBack = {},
                    )
                }
            }
        }

        DayOfWeek.entries.forEach { day ->
            composeRule.onNodeWithTag("weekday_${day.name}")
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
                .assertIsSelected()
        }
    }

    @Test
    fun primaryAndSecondaryContentMeetNormalTextContrast() {
        var primary = Color.Unspecified
        var onPrimary = Color.Unspecified
        var secondary = Color.Unspecified
        var onSecondary = Color.Unspecified
        composeRule.setContent {
            WakeMoveTheme {
                primary = MaterialTheme.colorScheme.primary
                onPrimary = MaterialTheme.colorScheme.onPrimary
                secondary = MaterialTheme.colorScheme.secondary
                onSecondary = MaterialTheme.colorScheme.onSecondary
            }
        }

        composeRule.runOnIdle {
            assertTrue(contrastRatio(primary, onPrimary) >= 4.5)
            assertTrue(contrastRatio(secondary, onSecondary) >= 4.5)
        }
    }

    @Test
    fun selectedNavigationAndChallengePairsMeetTextAndGraphicContrast() {
        var selectedForeground = Color.Unspecified
        var navigationSurface = Color.Unspecified
        var selectedContainer = Color.Unspecified
        composeRule.setContent {
            WakeMoveTheme {
                selectedForeground = MaterialTheme.colorScheme.primary
                navigationSurface = MaterialTheme.colorScheme.surface
                selectedContainer = MaterialTheme.colorScheme.primaryContainer
            }
        }

        composeRule.runOnIdle {
            assertTrue(
                "Selected navigation label contrast must be at least 4.5:1",
                contrastRatio(selectedForeground, navigationSurface) >= 4.5,
            )
            assertTrue(
                "Selected navigation icon contrast must be at least 3:1",
                contrastRatio(selectedForeground, selectedContainer) >= 3.0,
            )
            assertTrue(
                "Selected challenge border and check contrast must be at least 3:1",
                contrastRatio(selectedForeground, selectedContainer) >= 3.0,
            )
        }
    }

    private fun setNavContent(
        repository: FakeAlarmRepository,
        scheduler: FakeAlarmScheduler,
    ) {
        composeRule.setContent {
            WakeMoveTheme {
                WakeMoveNavHost(repository, scheduler, { readyHealth })
            }
        }
    }

    private class FakeAlarmRepository : AlarmRepository {
        private val alarms = linkedMapOf<String, Alarm>()
        private val alarmFlow = MutableStateFlow<List<Alarm>>(emptyList())

        val alarmCount: Int
            get() = alarms.size

        override fun observeAlarms(): Flow<List<Alarm>> = alarmFlow

        override suspend fun upsertAlarm(alarm: Alarm) {
            alarms[alarm.id] = alarm
            alarmFlow.value = alarms.values.toList()
        }

        override suspend fun deleteAlarm(id: String) {
            alarms.remove(id)
            alarmFlow.value = alarms.values.toList()
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
    }

    private class FakeAlarmScheduler(
        var failuresRemaining: Int = 0,
        private val onReschedule: suspend () -> Unit = {},
    ) : AlarmScheduler {
        var rescheduleCount = 0

        override fun schedule(alarm: Alarm, at: Instant) = Unit
        override fun cancel(alarmId: String) = Unit

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
        private val readyHealth = HealthSnapshot(
            exactAlarm = HealthStatus.READY,
            notifications = HealthStatus.READY,
            fullScreenIntent = HealthStatus.READY,
            camera = HealthStatus.READY,
            microphone = HealthStatus.READY,
        )

        private fun contrastRatio(first: Color, second: Color): Double {
            val lighter = maxOf(first.luminance(), second.luminance()).toDouble()
            val darker = minOf(first.luminance(), second.luminance()).toDouble()
            return (lighter + 0.05) / (darker + 0.05)
        }
    }
}
