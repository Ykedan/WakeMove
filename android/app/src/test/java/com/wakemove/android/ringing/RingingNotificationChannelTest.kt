package com.wakemove.android.ringing

import android.app.NotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RingingNotificationChannelTest {
    @Test
    fun `application initialization creates the high importance alarm channel`() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(NotificationManager::class.java)

        val channel = manager.getNotificationChannel(RingingService.NOTIFICATION_CHANNEL_ID)

        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
    }
}
