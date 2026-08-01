package com.wakemove.android.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppUpdateModelsTest {
    @Test
    fun semanticVersionComparisonHandlesPrefixesAndDifferentLengths() {
        assertTrue(isVersionNewer("v1.4.1", "1.4.0"))
        assertTrue(isVersionNewer("2.0", "1.99.99"))
        assertFalse(isVersionNewer("v1.4.0", "1.4.0"))
        assertFalse(isVersionNewer("1.3.9", "1.4.0"))
    }

    @Test
    fun releaseParserSelectsApkAssetAndNormalizesTag() {
        val release = GitHubUpdateRepository().parseRelease(
            """
            {
              "versionCode": 6,
              "versionName": "1.5.0",
              "releaseUrl": "https://github.com/Ykedan/WakeMove/releases/tag/v1.5.0",
              "releaseNotes": "新增软件内更新。",
              "downloadUrl": "https://github.com/Ykedan/WakeMove/releases/download/v1.5.0/WakeMove-v1.5.0.apk",
              "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            }
            """.trimIndent(),
        )

        assertEquals("1.5.0", release.versionName)
        assertEquals("新增软件内更新。", release.releaseNotes)
        assertTrue(release.downloadUrl.endsWith("WakeMove-v1.5.0.apk"))
    }

    @Test(expected = UpdateCheckException::class)
    fun releaseParserRejectsUntrustedDownloadHost() {
        GitHubUpdateRepository().parseRelease(
            """
            {
              "versionCode": 9,
              "versionName": "9.0.0",
              "releaseUrl": "https://github.com/Ykedan/WakeMove/releases/tag/v9.0.0",
              "downloadUrl": "https://example.com/WakeMove.apk",
              "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            }
            """.trimIndent(),
        )
    }
}
