package com.wakemove.android.challenge

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class OfflineChineseSpeechRecognitionSourceTest {
    @Test
    fun `vosk result text is read and trimmed`() {
        assertEquals(
            "今天 也 要 准时 起床",
            parseVoskText("""{"text":" 今天 也 要 准时 起床 "}""", "text"),
        )
    }

    @Test
    fun `malformed or missing vosk text is treated as empty`() {
        assertEquals("", parseVoskText("""{"partial":"今天"}""", "text"))
        assertEquals("", parseVoskText("not-json", "text"))
    }
}
