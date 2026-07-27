package com.wakemove.android.challenge

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechNormalizerTest {
    @Test
    fun removes_unicode_whitespace_and_punctuation() {
        assertEquals(
            "今天也要准时起床",
            SpeechNormalizer.normalize("　今天，也要！\n准时\t起床。"),
        )
    }

    @Test
    fun applies_nfkc_to_full_width_letters_and_arabic_digits() {
        assertEquals(
            "WakeMove123",
            SpeechNormalizer.normalize("ＷａｋｅＭｏｖｅ１２３"),
        )
    }

    @Test
    fun maps_chinese_and_arabic_digits_to_the_same_ascii_form() {
        assertEquals(
            "001234567890123456789",
            SpeechNormalizer.normalize("零〇一二三四五六七八九 ０１２３４５６７８９"),
        )
    }
}
