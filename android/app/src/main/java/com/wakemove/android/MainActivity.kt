package com.wakemove.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.wakemove.android.ringing.RingingService
import com.wakemove.android.ui.navigation.AlarmUiDependencies
import com.wakemove.android.ui.navigation.WakeMoveNavHost
import com.wakemove.android.ui.theme.WakeMoveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        applyRingingWindowFlags(intent)
        super.onCreate(savedInstanceState)
        setContent {
            WakeMoveTheme {
                val dependencies = application as AlarmUiDependencies
                WakeMoveNavHost(
                    repository = dependencies.alarmRepository,
                    scheduler = dependencies.alarmScheduler,
                    healthProvider = dependencies.healthService::snapshot,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyRingingWindowFlags(intent)
    }

    private fun applyRingingWindowFlags(intent: Intent) {
        if (intent.action != RingingService.ACTION_SHOW_RINGING) return
        setShowWhenLocked(true)
        setTurnScreenOn(true)
    }
}
