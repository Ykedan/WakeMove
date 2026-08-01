package com.wakemove.android.update

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppUpdateManagerTest {
    private val context
        get() = RuntimeEnvironment.getApplication()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val update = AppUpdateInfo(
        versionCode = 90,
        versionName = "9.0.0",
        downloadUrl = "https://github.com/Ykedan/WakeMove/releases/download/v9.0.0/WakeMove.apk",
        releaseUrl = "https://github.com/Ykedan/WakeMove/releases/tag/v9.0.0",
        releaseNotes = "测试更新",
        sha256 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    )

    @Before
    fun clearPreferences() {
        context.getSharedPreferences(AppUpdateManager.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun manualCheckShowsAvailableUpdate() = runBlocking {
        val manager = AppUpdateManager(context, scope, UpdateRepository { update })

        manager.checkForUpdate(manual = true)
        waitUntil { manager.state.value.phase == AppUpdatePhase.AVAILABLE }

        assertTrue(manager.state.value.showDialog)
        assertEquals("9.0.0", manager.state.value.info?.versionName)
    }

    @Test
    fun ignoredVersionStaysQuietDuringAutomaticCheck() = runBlocking {
        context.getSharedPreferences(AppUpdateManager.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(AppUpdateManager.KEY_IGNORED_VERSION, "9.0.0")
            .commit()
        val manager = AppUpdateManager(context, scope, UpdateRepository { update })

        manager.checkForUpdate(manual = false)
        waitUntil { manager.state.value.phase == AppUpdatePhase.IDLE }

        assertFalse(manager.state.value.showDialog)
        assertEquals("已忽略 WakeMove v9.0.0", manager.state.value.message)
    }

    private suspend fun waitUntil(condition: () -> Boolean) {
        repeat(100) {
            if (condition()) return
            delay(10)
        }
        error("Timed out waiting for update state")
    }
}
