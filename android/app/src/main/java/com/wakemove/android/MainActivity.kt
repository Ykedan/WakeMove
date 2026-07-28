package com.wakemove.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.edit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.wakemove.android.ringing.RingingService
import com.wakemove.android.domain.SessionStatus
import com.wakemove.android.ui.navigation.AlarmUiDependencies
import com.wakemove.android.ui.navigation.WakeMoveNavHost
import com.wakemove.android.ui.onboarding.OnboardingScreen
import com.wakemove.android.ui.theme.WakeMoveTheme

class MainActivity : ComponentActivity() {
    private var ringingOnlyLaunch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        applyRingingWindowFlags(intent)
        super.onCreate(savedInstanceState)
        setContent {
            WakeMoveTheme {
                val dependencies = application as AlarmUiDependencies
                val preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
                var onboardingComplete by rememberSaveable {
                    mutableStateOf(preferences.getBoolean(KEY_ONBOARDING_COMPLETE, false))
                }
                val ringingState by dependencies.ringingSessionController.state.collectAsState()
                LaunchedEffect(ringingState.session?.status) {
                    syncRingingWindow(ringingState.session?.status)
                }
                if (onboardingComplete ||
                    ringingState.session?.status == SessionStatus.RINGING
                ) {
                    WakeMoveNavHost(
                        repository = dependencies.alarmRepository,
                        scheduler = dependencies.alarmScheduler,
                        healthProvider = dependencies.healthService::snapshot,
                        ringingController = dependencies.ringingSessionController,
                    )
                } else {
                    OnboardingScreen(
                        onComplete = {
                            preferences.edit {
                                putBoolean(KEY_ONBOARDING_COMPLETE, true)
                            }
                            onboardingComplete = true
                        },
                    )
                }
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
        ringingOnlyLaunch = true
        setShowWhenLocked(true)
        setTurnScreenOn(true)
    }

    internal fun syncRingingWindow(status: SessionStatus?) {
        if (status == SessionStatus.RINGING) return
        setShowWhenLocked(false)
        setTurnScreenOn(false)
        if (ringingOnlyLaunch && !isFinishing) finish()
        ringingOnlyLaunch = false
    }

    private companion object {
        const val PREFERENCES_NAME = "wakemove_preferences"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    }
}
