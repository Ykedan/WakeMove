package com.wakemove.android.ui.health

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPermissionPolicyTest {
    @Test
    fun `first runtime request explains purpose before requesting`() {
        assertEquals(
            NotificationRepairAction.SHOW_RATIONALE,
            notificationRepairAction(
                apiLevel = 35,
                permissionGranted = false,
                requestedBefore = false,
                shouldShowRationale = false,
            ),
        )
    }

    @Test
    fun `temporary denial keeps runtime rationale and retry path`() {
        assertEquals(
            NotificationRepairAction.SHOW_RATIONALE,
            notificationRepairAction(
                apiLevel = 35,
                permissionGranted = false,
                requestedBefore = true,
                shouldShowRationale = true,
            ),
        )
    }

    @Test
    fun `permanent denial and non runtime platforms use settings`() {
        assertEquals(
            NotificationRepairAction.OPEN_SETTINGS,
            notificationRepairAction(
                apiLevel = 35,
                permissionGranted = false,
                requestedBefore = true,
                shouldShowRationale = false,
            ),
        )
        assertEquals(
            NotificationRepairAction.OPEN_SETTINGS,
            notificationRepairAction(
                apiLevel = 32,
                permissionGranted = true,
                requestedBefore = false,
                shouldShowRationale = false,
            ),
        )
    }
}
