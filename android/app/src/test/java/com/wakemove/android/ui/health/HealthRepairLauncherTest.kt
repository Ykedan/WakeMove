package com.wakemove.android.ui.health

import android.provider.Settings
import com.wakemove.android.ringing.RingingService
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HealthRepairLauncherTest {
    @Test
    fun `notification channel repair opens the exact alarm channel settings`() {
        val context = RuntimeEnvironment.getApplication()

        launchHealthRepair(context, HealthIssue.NOTIFICATION_CHANNEL)

        val intent = shadowOf(context).nextStartedActivity
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        assertEquals(
            RingingService.NOTIFICATION_CHANNEL_ID,
            intent.getStringExtra(Settings.EXTRA_CHANNEL_ID),
        )
    }
}
