package com.wakemove.android.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID) ?: return
        (context.applicationContext as? SchedulingDependencies)
            ?.alarmScheduler
            ?.onAlarmDelivered(alarmId)
        val serviceIntent = Intent(ACTION_START_RINGING)
            .setClassName(context, RINGING_SERVICE_CLASS_NAME)
            .putExtra(EXTRA_ALARM_ID, alarmId)
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    companion object {
        const val ACTION_ALARM_FIRED = "com.wakemove.android.action.ALARM_FIRED"
        const val ACTION_START_RINGING = "com.wakemove.android.action.START_RINGING"
        const val EXTRA_ALARM_ID = "com.wakemove.android.extra.ALARM_ID"

        private const val RINGING_SERVICE_CLASS_NAME =
            "com.wakemove.android.ringing.RingingService"
    }
}
