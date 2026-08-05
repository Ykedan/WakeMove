package com.wakemove.android.i18n

import com.wakemove.android.ui.settings.LanguagePreference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EnglishCatalogTest {
    @After
    fun resetLocale() {
        WakeMoveLocale.select(LanguagePreference.SIMPLIFIED_CHINESE)
    }

    @Test
    fun `english locale translates core screens`() {
        WakeMoveLocale.select(LanguagePreference.ENGLISH)

        assertEquals("Settings", tr("设置"))
        assertEquals("New alarm", tr("新建闹钟"))
        assertEquals("Health Check", tr("健康检查"))
        assertEquals("Ringing", tr("正在响铃"))
        assertEquals("Offline speech recognition", tr("离线语音识别"))
    }

    @Test
    fun `english locale translates dynamic copy`() {
        WakeMoveLocale.select(LanguagePreference.ENGLISH)

        val samples = listOf(
            tr("WakeMove v1.6.0 可以更新"),
            tr("正在下载，已完成 42%"),
            tr("剩余贪睡 2 次"),
            tr("下一次响铃：Tomorrow 07:30（In about 8 hr）"),
            tr("闹钟时间 07:30"),
        )

        samples.forEach { translated ->
            assertFalse("Chinese remained in: $translated", HAN_REGEX.containsMatchIn(translated))
        }
    }

    @Test
    fun `explicit Chinese locale keeps Chinese copy`() {
        WakeMoveLocale.select(LanguagePreference.SIMPLIFIED_CHINESE)
        assertEquals("设置", tr("设置"))
    }

    private companion object {
        val HAN_REGEX = Regex("[\\u4e00-\\u9fff]")
    }
}
