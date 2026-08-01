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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.wakemove.android.ringing.RingingService
import com.wakemove.android.domain.SessionStatus
import com.wakemove.android.ui.navigation.AlarmUiDependencies
import com.wakemove.android.ui.navigation.WakeMoveNavHost
import com.wakemove.android.ui.onboarding.OnboardingScreen
import com.wakemove.android.ui.onboarding.StartupPermissionPrompt
import com.wakemove.android.ui.settings.WakeMovePreferences
import com.wakemove.android.ui.theme.WakeMoveTheme

class MainActivity : ComponentActivity() {
    private var ringingOnlyLaunch = false
    private var observedRingingSession = false

    override fun onCreate(savedInstanceState: Bundle?) {
        applyRingingWindowFlags(intent)
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(EXTRA_SHOW_UPDATE, false)) {
            (application as AlarmUiDependencies).appUpdateManager.checkForUpdate(manual = true)
            intent.removeExtra(EXTRA_SHOW_UPDATE)
        }
        setContent {
            val settingsStore = remember { WakeMovePreferences(this@MainActivity) }
            var appSettings by remember { mutableStateOf(settingsStore.load()) }
            WakeMoveTheme(
                themePreference = appSettings.theme,
                useDynamicColor = appSettings.useDynamicColor,
            ) {
                val dependencies = application as AlarmUiDependencies
                val preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
                var onboardingComplete by rememberSaveable {
                    mutableStateOf(preferences.getBoolean(KEY_ONBOARDING_COMPLETE, false))
                }
                var permissionPromptHandled by rememberSaveable {
                    mutableStateOf(
                        preferences.getBoolean(KEY_PERMISSION_PROMPT_HANDLED, false),
                    )
                }
                val ringingState by dependencies.ringingSessionController.state.collectAsState()
                LaunchedEffect(ringingState.session?.status) {
                    syncRingingWindow(ringingState.session?.status)
                }
                LaunchedEffect(onboardingComplete, permissionPromptHandled) {
                    if (onboardingComplete && permissionPromptHandled) {
                        dependencies.appUpdateManager.checkForUpdate(manual = false)
                    }
                }
                if (onboardingComplete ||
                    ringingState.session?.status == SessionStatus.RINGING
                ) {
                    WakeMoveNavHost(
                        repository = dependencies.alarmRepository,
                        scheduler = dependencies.alarmScheduler,
                        healthProvider = dependencies.healthService::snapshot,
                        ringingController = dependencies.ringingSessionController,
                        settings = appSettings,
                        onSettingsChange = { updated ->
                            settingsStore.save(updated)
                            appSettings = updated
                        },
                        updateManager = dependencies.appUpdateManager,
                    )
                    if (onboardingComplete &&
                        !permissionPromptHandled &&
                        ringingState.session?.status != SessionStatus.RINGING
                    ) {
                        StartupPermissionPrompt(
                            healthProvider = dependencies.healthService::snapshot,
                            onFinished = {
                                preferences.edit {
                                    putBoolean(KEY_PERMISSION_PROMPT_HANDLED, true)
                                }
                                permissionPromptHandled = true
                            },
                        )
                    }
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
        if (intent.getBooleanExtra(EXTRA_SHOW_UPDATE, false)) {
            (application as AlarmUiDependencies).appUpdateManager.checkForUpdate(manual = true)
            intent.removeExtra(EXTRA_SHOW_UPDATE)
        }
    }

    override fun onResume() {
        super.onResume()
        (application as? AlarmUiDependencies)
            ?.appUpdateManager
            ?.continueInstallationIfPossible()
    }

    private fun applyRingingWindowFlags(intent: Intent) {
        if (intent.action != RingingService.ACTION_SHOW_RINGING) return
        ringingOnlyLaunch = true
        setShowWhenLocked(true)
        setTurnScreenOn(true)
    }

    internal fun syncRingingWindow(status: SessionStatus?) {
        if (status == SessionStatus.RINGING) {
            observedRingingSession = true
            return
        }
        if (!observedRingingSession) return
        setShowWhenLocked(false)
        setTurnScreenOn(false)
        if (ringingOnlyLaunch && !isFinishing) finish()
        ringingOnlyLaunch = false
        observedRingingSession = false
    }

    companion object {
        const val EXTRA_SHOW_UPDATE = "com.wakemove.android.extra.SHOW_UPDATE"
        const val PREFERENCES_NAME = "wakemove_preferences"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        const val KEY_PERMISSION_PROMPT_HANDLED = "startup_permission_prompt_handled_v1"
    }
}
