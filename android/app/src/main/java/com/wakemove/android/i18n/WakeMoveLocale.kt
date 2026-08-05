package com.wakemove.android.i18n

import android.content.Context
import com.wakemove.android.ui.settings.LanguagePreference
import java.util.Locale

/** Lightweight process-wide locale used by UI, notifications and background services. */
object WakeMoveLocale {
    @Volatile
    private var preference: LanguagePreference = LanguagePreference.FOLLOW_SYSTEM

    fun initialize(context: Context) {
        val stored = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
        preference = stored
            ?.let { runCatching { enumValueOf<LanguagePreference>(it) }.getOrNull() }
            ?: LanguagePreference.FOLLOW_SYSTEM
    }

    fun select(value: LanguagePreference) {
        preference = value
    }

    fun isEnglish(): Boolean = when (preference) {
        LanguagePreference.ENGLISH -> true
        LanguagePreference.SIMPLIFIED_CHINESE -> false
        LanguagePreference.FOLLOW_SYSTEM -> Locale.getDefault().language != Locale.CHINESE.language
    }

    fun currentLocale(): Locale = if (isEnglish()) Locale.ENGLISH else Locale.SIMPLIFIED_CHINESE

    private const val PREFERENCES_NAME = "wakemove_preferences"
    private const val KEY_LANGUAGE = "app_language_v1"
}

fun tr(chinese: String, english: String): String =
    if (WakeMoveLocale.isEnglish()) english else chinese

fun tr(chinese: String): String =
    if (WakeMoveLocale.isEnglish()) EnglishCatalog.translate(chinese) else chinese
