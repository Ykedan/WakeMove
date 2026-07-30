package com.wakemove.android.ui.onboarding

import com.wakemove.android.health.HealthSnapshot
import com.wakemove.android.health.HealthStatus
import com.wakemove.android.ui.health.HealthIssue
import org.junit.Assert.assertEquals
import org.junit.Test

class StartupPermissionPromptTest {
    @Test
    fun `special permissions are opened in alarm critical order`() {
        val snapshot = HealthSnapshot(
            exactAlarm = HealthStatus.ACTION_REQUIRED,
            notifications = HealthStatus.ACTION_REQUIRED,
            fullScreenIntent = HealthStatus.ACTION_REQUIRED,
            camera = HealthStatus.ACTION_REQUIRED,
            microphone = HealthStatus.ACTION_REQUIRED,
            batteryOptimization = HealthStatus.ACTION_REQUIRED,
        )

        assertEquals(
            listOf(
                HealthIssue.EXACT_ALARM,
                HealthIssue.FULL_SCREEN_INTENT,
                HealthIssue.BATTERY_OPTIMIZATION,
            ),
            startupSpecialIssues(snapshot),
        )
    }

    @Test
    fun `ready and unavailable special capabilities do not open settings`() {
        val snapshot = HealthSnapshot(
            exactAlarm = HealthStatus.READY,
            notifications = HealthStatus.READY,
            fullScreenIntent = HealthStatus.UNAVAILABLE,
            camera = HealthStatus.READY,
            microphone = HealthStatus.READY,
            batteryOptimization = HealthStatus.READY,
        )

        assertEquals(emptyList<HealthIssue>(), startupSpecialIssues(snapshot))
    }
}
