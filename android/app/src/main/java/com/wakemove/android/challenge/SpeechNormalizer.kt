package com.wakemove.android.challenge

import java.text.Normalizer

object SpeechNormalizer {
    fun normalize(text: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
        val result = StringBuilder(normalized.length)
        var offset = 0

        while (offset < normalized.length) {
            val codePoint = normalized.codePointAt(offset)
            offset += Character.charCount(codePoint)
            if (codePoint.isWhitespaceOrPunctuation()) continue

            val digit = codePoint.toNormalizedDigit()
            if (digit != null) {
                result.append(digit)
            } else {
                result.appendCodePoint(codePoint)
            }
        }

        return result.toString()
    }
}

private fun Int.isWhitespaceOrPunctuation(): Boolean =
    Character.isWhitespace(this) ||
        when (Character.getType(this)) {
            Character.SPACE_SEPARATOR.toInt(),
            Character.LINE_SEPARATOR.toInt(),
            Character.PARAGRAPH_SEPARATOR.toInt(),
            Character.CONNECTOR_PUNCTUATION.toInt(),
            Character.DASH_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt(),
            Character.END_PUNCTUATION.toInt(),
            Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
            Character.FINAL_QUOTE_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt(),
            -> true

            else -> false
        }

private fun Int.toNormalizedDigit(): Char? = when (this) {
    '零'.code, '〇'.code -> '0'
    '一'.code -> '1'
    '二'.code -> '2'
    '三'.code -> '3'
    '四'.code -> '4'
    '五'.code -> '5'
    '六'.code -> '6'
    '七'.code -> '7'
    '八'.code -> '8'
    '九'.code -> '9'
    else -> null
}
