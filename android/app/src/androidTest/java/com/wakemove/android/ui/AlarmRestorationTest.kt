package com.wakemove.android.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.StateRestorationTester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.wakemove.android.domain.Alarm
import com.wakemove.android.domain.AlarmEvent
import com.wakemove.android.domain.AlarmRepository
import com.wakemove.android.domain.PendingAlarmSchedule
import com.wakemove.android.domain.RingingSession
import com.wakemove.android.domain.SessionStatus
import com.wakemove.android.health.HealthSnapshot
import com.wakemove.android.health.HealthStatus
import com.wakemove.android.scheduling.AlarmScheduler
import com.wakemove.android.ui.navigation.WakeMoveNavHost
import com.wakemove.android.ui.theme.WakeMoveTheme
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test

class AlarmRestorationTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun editorRouteAndDraftSurviveSavedStateRestoration() = runComposeUiTest {
        val repository = RestorationRepository()
        val restorationTester = StateRestorationTester(this)
        restorationTester.setContent {
            WakeMoveTheme {
                WakeMoveNavHost(repository, NoOpScheduler, { readyHealth })
            }
        }

        onNodeWithTag("add_alarm").performClick()
        onNodeWithTag("alarm_time").performTextReplacement("06:45")
        onNodeWithTag("weekday_FRIDAY").performClick()

        restorationTester.emulateSaveAndRestore()

        onNodeWithTag("alarm_time").assertIsDisplayed().assertTextContains("06:45")
        onNodeWithTag("weekday_FRIDAY").assertIsSelected()
    }

    private class RestorationRepository : AlarmRepository {
        private val alarms = MutableStateFlow<List<Alarm>>(emptyList())

        override fun observeAlarms(): Flow<List<Alarm>> = alarms
        override suspend fun upsertAlarm(alarm: Alarm) {
            alarms.value = alarms.value.filterNot { it.id == alarm.id } + alarm
        }

        override suspend fun deleteAlarm(id: String) {
            alarms.value = alarms.value.filterNot { it.id == id }
        }

        override suspend fun getAlarm(id: String): Alarm? =
            alarms.value.firstOrNull { it.id == id }

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

    private data object NoOpScheduler : AlarmScheduler {
        override fun schedule(alarm: Alarm, at: Instant) = Unit
        override fun cancel(alarmId: String) = Unit
        override suspend fun rescheduleAll() = Unit
    }

    companion object {
        private val readyHealth = HealthSnapshot(
            exactAlarm = HealthStatus.READY,
            notifications = HealthStatus.READY,
            fullScreenIntent = HealthStatus.READY,
            camera = HealthStatus.READY,
            microphone = HealthStatus.READY,
        )
    }
}
