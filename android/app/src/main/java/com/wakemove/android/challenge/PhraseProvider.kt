package com.wakemove.android.challenge

import android.content.res.AssetManager
import java.security.SecureRandom
import org.json.JSONArray

class PhraseProvider(
    private val assets: AssetManager,
    private val random: SecureRandom = SecureRandom(),
) {
    private val phrases: List<String> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { reader ->
            val json = JSONArray(reader.readText())
            buildList(json.length()) {
                repeat(json.length()) { index ->
                    add(json.getString(index))
                }
            }.also { loaded ->
                require(loaded.isNotEmpty()) { "Phrase asset must not be empty" }
                require(loaded.all(String::isNotBlank)) {
                    "Phrase asset must contain only non-blank strings"
                }
            }
        }
    }

    fun nextPhrase(): String = phrases[random.nextInt(phrases.size)]

    private companion object {
        const val ASSET_PATH = "phrases/zh-CN.json"
    }
}
