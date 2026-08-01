package com.wakemove.android.ui.settings

import android.content.Context

enum class ThemePreference {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
}

enum class LanguagePreference {
    FOLLOW_SYSTEM,
    SIMPLIFIED_CHINESE,
}

data class WakeMoveSettings(
    val theme: ThemePreference = ThemePreference.FOLLOW_SYSTEM,
    val useDynamicColor: Boolean = true,
    val language: LanguagePreference = LanguagePreference.FOLLOW_SYSTEM,
)

class WakeMovePreferences(context: Context) {
    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): WakeMoveSettings = WakeMoveSettings(
        theme = preferences.getString(KEY_THEME, null)
            ?.let { runCatching { enumValueOf<ThemePreference>(it) }.getOrNull() }
            ?: ThemePreference.FOLLOW_SYSTEM,
        useDynamicColor = preferences.getBoolean(KEY_DYNAMIC_COLOR, true),
        language = preferences.getString(KEY_LANGUAGE, null)
            ?.let { runCatching { enumValueOf<LanguagePreference>(it) }.getOrNull() }
            ?: LanguagePreference.FOLLOW_SYSTEM,
    )

    fun save(settings: WakeMoveSettings) {
        preferences.edit()
            .putString(KEY_THEME, settings.theme.name)
            .putBoolean(KEY_DYNAMIC_COLOR, settings.useDynamicColor)
            .putString(KEY_LANGUAGE, settings.language.name)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "wakemove_preferences"
        const val KEY_THEME = "appearance_theme_v1"
        const val KEY_DYNAMIC_COLOR = "appearance_dynamic_color_v1"
        const val KEY_LANGUAGE = "app_language_v1"
    }
}
