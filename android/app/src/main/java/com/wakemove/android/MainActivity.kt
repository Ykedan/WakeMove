package com.wakemove.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.wakemove.android.ringing.RingingService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        applyRingingWindowFlags(intent)
        super.onCreate(savedInstanceState)
        setContent {
            WakeMoveTheme {
                Text("WakeMove")
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

@Composable
private fun WakeMoveTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
