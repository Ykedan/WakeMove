package com.wakemove.android.ui.health

enum class NotificationRepairAction {
    SHOW_RATIONALE,
    OPEN_SETTINGS,
}

fun notificationRepairAction(
    apiLevel: Int,
    permissionGranted: Boolean,
    requestedBefore: Boolean,
    shouldShowRationale: Boolean,
): NotificationRepairAction {
    if (apiLevel < 33 || permissionGranted) return NotificationRepairAction.OPEN_SETTINGS
    return if (!requestedBefore || shouldShowRationale) {
        NotificationRepairAction.SHOW_RATIONALE
    } else {
        NotificationRepairAction.OPEN_SETTINGS
    }
}
