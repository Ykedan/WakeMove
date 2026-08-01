package com.wakemove.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import com.wakemove.android.ui.settings.AppUpdateDialog
import com.wakemove.android.ui.theme.WakeMoveTheme
import com.wakemove.android.update.AppUpdateInfo
import com.wakemove.android.update.AppUpdatePhase
import com.wakemove.android.update.AppUpdateUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppUpdateDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun availableUpdateExplainsVersionAndStartsDownload() {
        var downloadRequested = false
        composeRule.setContent {
            WakeMoveTheme {
                AppUpdateDialog(
                    state = AppUpdateUiState(
                        phase = AppUpdatePhase.AVAILABLE,
                        info = AppUpdateInfo(
                            versionCode = 90,
                            versionName = "9.0.0",
                            downloadUrl = "https://github.com/WakeMove.apk",
                            releaseUrl = "https://github.com/release",
                            releaseNotes = "新增软件内更新。",
                            sha256 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        ),
                        showDialog = true,
                    ),
                    onDismiss = {},
                    onDownload = { downloadRequested = true },
                    onInstall = {},
                    onRetry = {},
                    onIgnoreVersion = {},
                )
            }
        }

        composeRule.onNodeWithTag("app_update_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("WakeMove v9.0.0 可以更新").assertIsDisplayed()
        composeRule.onNodeWithText("新增软件内更新。").assertIsDisplayed()
        composeRule.onNodeWithTag("download_update").performClick()
        composeRule.runOnIdle { assertTrue(downloadRequested) }
    }

    @Test
    fun readyUpdateRequestsSystemInstallation() {
        var installRequested = false
        composeRule.setContent {
            WakeMoveTheme {
                AppUpdateDialog(
                    state = AppUpdateUiState(
                        phase = AppUpdatePhase.READY_TO_INSTALL,
                        info = AppUpdateInfo(
                            versionCode = 90,
                            versionName = "9.0.0",
                            downloadUrl = "",
                            releaseUrl = "",
                            releaseNotes = "",
                            sha256 = "",
                        ),
                        showDialog = true,
                        downloadId = 42L,
                    ),
                    onDismiss = {},
                    onDownload = {},
                    onInstall = { installRequested = true },
                    onRetry = {},
                    onIgnoreVersion = {},
                )
            }
        }

        composeRule.onNodeWithText("安装更新").performClick()
        composeRule.runOnIdle { assertTrue(installRequested) }
    }

    @Test
    fun pendingDownloadDoesNotPretendToBeZeroPercent() {
        composeRule.setContent {
            WakeMoveTheme {
                AppUpdateDialog(
                    state = AppUpdateUiState(
                        phase = AppUpdatePhase.DOWNLOADING,
                        progressPercent = null,
                        showDialog = true,
                    ),
                    onDismiss = {},
                    onDownload = {},
                    onInstall = {},
                    onRetry = {},
                    onIgnoreVersion = {},
                )
            }
        }

        composeRule.onNodeWithText("正在获取安装包大小…").assertIsDisplayed()
        composeRule.onAllNodesWithText("已下载 0%").assertCountEquals(0)
    }
}
