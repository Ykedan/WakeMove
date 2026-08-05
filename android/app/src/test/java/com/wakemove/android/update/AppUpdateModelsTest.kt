package com.wakemove.android.update

import com.wakemove.android.i18n.WakeMoveLocale
import com.wakemove.android.ui.settings.LanguagePreference
import org.junit.After
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
    @After
    fun resetLocale() {
        WakeMoveLocale.select(LanguagePreference.SIMPLIFIED_CHINESE)
    }

    @Test
    fun semanticVersionComparisonHandlesPrefixesAndDifferentLengths() {
        assertTrue(isVersionNewer("v1.4.1", "1.4.0"))
        assertTrue(isVersionNewer("2.0", "1.99.99"))
        assertFalse(isVersionNewer("v1.4.0", "1.4.0"))
        assertFalse(isVersionNewer("1.3.9", "1.4.0"))
    }

    @Test
    fun releaseParserSelectsApkAssetAndNormalizesTag() {
        WakeMoveLocale.select(LanguagePreference.SIMPLIFIED_CHINESE)
        val release = GitHubUpdateRepository().parseRelease(
            """
            {
              "versionCode": 6,
              "versionName": "1.5.0",
              "releaseUrl": "https://github.com/Ykedan/WakeMove/releases/tag/v1.5.0",
              "releaseNotes": "新增软件内更新。",
              "downloadUrl": "https://github.com/Ykedan/WakeMove/releases/download/v1.5.0/WakeMove-v1.5.0.apk",
              "fallbackDownloadUrl": "https://ykedan.github.io/WakeMove/downloads/WakeMove-v1.5.0.apk",
              "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            }
            """.trimIndent(),
        )

        assertEquals("1.5.0", release.versionName)
        assertEquals("新增软件内更新。", release.releaseNotes)
        assertTrue(release.downloadUrl.endsWith("WakeMove-v1.5.0.apk"))
        assertTrue(release.fallbackDownloadUrl?.contains("ykedan.github.io") == true)
    }

    @Test
    fun releaseParserUsesEnglishNotesForEnglishInterface() {
        WakeMoveLocale.select(LanguagePreference.ENGLISH)
        val release = GitHubUpdateRepository().parseRelease(
            """
            {
              "versionCode": 8,
              "versionName": "1.6.0",
              "releaseUrl": "https://github.com/Ykedan/WakeMove/releases/tag/v1.6.0",
              "releaseNotes": "新增英文界面。",
              "releaseNotesEn": "Added a complete English interface.",
              "downloadUrl": "https://github.com/Ykedan/WakeMove/releases/download/v1.6.0/WakeMove-v1.6.0.apk",
              "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            }
            """.trimIndent(),
        )

        assertEquals("Added a complete English interface.", release.releaseNotes)
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

    @Test(expected = UpdateCheckException::class)
    fun releaseParserRejectsUntrustedFallbackHost() {
        GitHubUpdateRepository().parseRelease(
            """
            {
              "versionCode": 9,
              "versionName": "9.0.0",
              "releaseUrl": "https://github.com/Ykedan/WakeMove/releases/tag/v9.0.0",
              "downloadUrl": "https://github.com/Ykedan/WakeMove/releases/download/v9.0.0/WakeMove.apk",
              "fallbackDownloadUrl": "https://example.com/WakeMove.apk",
              "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            }
            """.trimIndent(),
        )
    }
}
