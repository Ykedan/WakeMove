package com.wakemove.android

import android.content.Intent
import com.wakemove.android.ringing.RingingService
import com.wakemove.android.domain.SessionStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityRingingIntentTest {
    @Test
    fun `ringing intent turns on and shows an existing activity over the lock screen`() {
        val controller = Robolectric.buildActivity(
            MainActivity::class.java,
            Intent(Intent.ACTION_MAIN),
        ).create()
        val activity = controller.get()
        assertFalse(shadowOf(activity).showWhenLocked)
        assertFalse(shadowOf(activity).turnScreenOn)

        controller.newIntent(
            Intent(activity, MainActivity::class.java)
                .setAction(RingingService.ACTION_SHOW_RINGING),
        )

        assertTrue(shadowOf(activity).showWhenLocked)
        assertTrue(shadowOf(activity).turnScreenOn)
    }

    @Test
    fun `terminal ringing launch clears lock screen flags and finishes ringing only activity`() {
        val controller = Robolectric.buildActivity(
            MainActivity::class.java,
            Intent(Intent.ACTION_MAIN),
        ).create()
        val activity = controller.get()
        controller.newIntent(
            Intent(activity, MainActivity::class.java)
                .setAction(RingingService.ACTION_SHOW_RINGING),
        )
        assertTrue(shadowOf(activity).showWhenLocked)

        activity.syncRingingWindow(SessionStatus.RINGING)
        activity.syncRingingWindow(SessionStatus.COMPLETED)

        assertFalse(shadowOf(activity).showWhenLocked)
        assertFalse(shadowOf(activity).turnScreenOn)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `pre session ringing launch keeps lock screen flags until a ringing session is observed`() {
        val controller = Robolectric.buildActivity(
            MainActivity::class.java,
            Intent(Intent.ACTION_MAIN),
        ).create()
        val activity = controller.get()
        controller.newIntent(
            Intent(activity, MainActivity::class.java)
                .setAction(RingingService.ACTION_SHOW_RINGING),
        )

        activity.syncRingingWindow(null)

        assertTrue(shadowOf(activity).showWhenLocked)
        assertTrue(shadowOf(activity).turnScreenOn)
        assertFalse(activity.isFinishing)
    }
}
