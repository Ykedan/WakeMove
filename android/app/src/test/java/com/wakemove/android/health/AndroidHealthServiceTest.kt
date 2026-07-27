package com.wakemove.android.health

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class AndroidHealthServiceTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        shadowOf(context.packageManager).apply {
            setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, true)
            setSystemFeature(PackageManager.FEATURE_MICROPHONE, true)
        }
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.USE_FULL_SCREEN_INTENT,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
        )
        shadowOf(context.getSystemService(NotificationManager::class.java))
            .setNotificationsEnabled(true)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun `snapshot reports ready when required access and sensors are available`() {
        val snapshot = AndroidHealthService(
            context = context,
            fullScreenIntentAllowed = { true },
            batteryOptimizationIgnored = { true },
        ).snapshot()

        assertEquals(HealthStatus.READY, snapshot.exactAlarm)
        assertEquals(HealthStatus.READY, snapshot.notifications)
        assertEquals(HealthStatus.READY, snapshot.fullScreenIntent)
        assertEquals(HealthStatus.READY, snapshot.camera)
        assertEquals(HealthStatus.READY, snapshot.microphone)
        assertEquals(HealthStatus.READY, snapshot.batteryOptimization)
        assertTrue(snapshot.canScheduleAlarms)
    }

    @Test
    fun `snapshot separates action required from unavailable hardware`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.CAMERA,
        )
        shadowOf(context.packageManager)
            .setSystemFeature(PackageManager.FEATURE_MICROPHONE, false)

        val snapshot = AndroidHealthService(
            context = context,
            fullScreenIntentAllowed = { false },
            batteryOptimizationIgnored = { false },
        ).snapshot()

        assertEquals(HealthStatus.ACTION_REQUIRED, snapshot.exactAlarm)
        assertEquals(HealthStatus.ACTION_REQUIRED, snapshot.notifications)
        assertEquals(HealthStatus.ACTION_REQUIRED, snapshot.fullScreenIntent)
        assertEquals(HealthStatus.ACTION_REQUIRED, snapshot.camera)
        assertEquals(HealthStatus.UNAVAILABLE, snapshot.microphone)
        assertEquals(HealthStatus.ACTION_REQUIRED, snapshot.batteryOptimization)
        assertFalse(snapshot.canScheduleAlarms)
    }

    @Test
    fun `snapshot reports unavailable when full screen intent permission is absent`() {
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(
            Manifest.permission.USE_FULL_SCREEN_INTENT,
        )

        val snapshot = AndroidHealthService(
            context = context,
            fullScreenIntentAllowed = { true },
        ).snapshot()

        assertEquals(HealthStatus.UNAVAILABLE, snapshot.fullScreenIntent)
        assertFalse(snapshot.canScheduleAlarms)
    }
}
