package com.wakemove.android.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WakeMovePreferencesTest {
    private val context
        get() = RuntimeEnvironment.getApplication()

    @Before
    fun clearPreferences() {
        context.getSharedPreferences("wakemove_preferences", 0).edit().clear().commit()
    }

    @Test
    fun defaultsFollowDeviceAppearance() {
        assertEquals(
            WakeMoveSettings(),
            WakeMovePreferences(context).load(),
        )
    }

    @Test
    fun appearanceAndLanguageSurviveReload() {
        val expected = WakeMoveSettings(
            theme = ThemePreference.DARK,
            useDynamicColor = false,
            language = LanguagePreference.SIMPLIFIED_CHINESE,
        )

        WakeMovePreferences(context).save(expected)

        assertEquals(expected, WakeMovePreferences(context).load())
    }
}
