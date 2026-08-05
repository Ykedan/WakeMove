package com.wakemove.android.update

import android.content.Context
import android.os.Looper
import com.sun.net.httpserver.HttpServer
import com.wakemove.android.i18n.WakeMoveLocale
import com.wakemove.android.ui.settings.LanguagePreference
import java.net.InetSocketAddress
import java.security.MessageDigest
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
import org.robolectric.Shadows.shadowOf

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
        WakeMoveLocale.select(LanguagePreference.SIMPLIFIED_CHINESE)
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

    @Test
    fun websiteMirrorIsTriedBeforeReleaseDownload() {
        val manager = AppUpdateManager(context, scope, UpdateRepository { update })
        val mirrored = update.copy(
            fallbackDownloadUrl = "https://ykedan.github.io/WakeMove/downloads/WakeMove-v9.0.0.apk",
        )

        assertEquals(
            listOf(mirrored.fallbackDownloadUrl, mirrored.downloadUrl),
            manager.downloadCandidates(mirrored),
        )
    }

    @Test
    fun directDownloaderCompletesWithoutSystemDownloadManager() = runBlocking {
        val payload = ByteArray(256 * 1024) { index -> (index % 251).toByte() }
        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(payload)
            .joinToString("") { byte -> "%02x".format(byte) }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/WakeMove.apk") { exchange ->
                exchange.responseHeaders.add("Content-Type", "application/vnd.android.package-archive")
                exchange.sendResponseHeaders(200, payload.size.toLong())
                exchange.responseBody.use { it.write(payload) }
            }
            start()
        }
        try {
            val localUpdate = update.copy(
                downloadUrl = "http://127.0.0.1:${server.address.port}/WakeMove.apk",
                sha256 = sha256,
            )
            val manager = AppUpdateManager(context, scope, UpdateRepository { localUpdate })
            manager.checkForUpdate(manual = true)
            waitUntil { manager.state.value.phase == AppUpdatePhase.AVAILABLE }

            manager.downloadUpdate()

            waitUntil { manager.state.value.phase == AppUpdatePhase.READY_TO_INSTALL }
            assertEquals(100, manager.state.value.progressPercent)
            assertTrue(context.filesDir.resolve("updates/WakeMove-v9.0.0.apk").isFile)
        } finally {
            server.stop(0)
            context.filesDir.resolve("updates/WakeMove-v9.0.0.apk").delete()
        }
    }

    private suspend fun waitUntil(condition: () -> Boolean) {
        repeat(500) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            delay(10)
        }
        error("Timed out waiting for update state")
    }
}
