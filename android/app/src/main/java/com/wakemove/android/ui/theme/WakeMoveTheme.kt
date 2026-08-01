package com.wakemove.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Build
import com.wakemove.android.ui.settings.ThemePreference

val WakeMoveBackground = Color(0xFFF6F7FB)
val WakeMoveSurface = Color(0xFFFFFFFF)
val WakeMoveNight = Color(0xFF0D1324)
val WakeMoveNightElevated = Color(0xFF182039)
val WakeMoveBlue = Color(0xFF4056C7)
val WakeMoveSky = Color(0xFFBCD0FF)
val WakeMoveDawn = Color(0xFFFF7458)
val WakeMoveDawnSoft = Color(0xFFFFE4DD)
val WakeMoveMint = Color(0xFF55BFA4)
val WakeMoveMist = Color(0xFFE9EDF7)
val WakeMoveText = Color(0xFF171C2C)
val WakeMoveMutedText = Color(0xFF687086)
val WakeMoveErrorContainer = Color(0xFFFFE4E2)

// Compatibility aliases retained while screens migrate to the new “blue-hour dawn” system.
val WakeMoveSunrise = WakeMoveBlue
val WakeMoveSunriseForeground = Color(0xFF3045AA)
val WakeMoveSunlight = WakeMoveSky
val WakeMovePeach = WakeMoveMist

private val WakeMoveColors = lightColorScheme(
    primary = WakeMoveSunriseForeground,
    onPrimary = WakeMoveSurface,
    primaryContainer = WakeMovePeach,
    onPrimaryContainer = WakeMoveText,
    secondary = Color(0xFF9E3827),
    onSecondary = WakeMoveSurface,
    secondaryContainer = WakeMoveDawnSoft,
    onSecondaryContainer = WakeMoveText,
    background = WakeMoveBackground,
    onBackground = WakeMoveText,
    surface = WakeMoveSurface,
    onSurface = WakeMoveText,
    surfaceVariant = WakeMovePeach,
    onSurfaceVariant = WakeMoveMutedText,
    errorContainer = WakeMoveErrorContainer,
    onErrorContainer = WakeMoveText,
)

private val WakeMoveTypography = Typography(
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 52.sp,
        lineHeight = 56.sp,
        letterSpacing = (-1.5).sp,
        fontWeight = FontWeight.ExtraBold,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
        fontWeight = FontWeight.Bold,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
    ),
)

private val WakeMoveDarkColors = darkColorScheme(
    primary = WakeMoveSky,
    onPrimary = WakeMoveNight,
    primaryContainer = WakeMoveNightElevated,
    onPrimaryContainer = WakeMoveSky,
    secondary = WakeMoveDawn,
    onSecondary = WakeMoveNight,
    secondaryContainer = Color(0xFF512A2A),
    onSecondaryContainer = WakeMoveDawnSoft,
    background = WakeMoveNight,
    onBackground = Color(0xFFF1F3FA),
    surface = WakeMoveNightElevated,
    onSurface = Color(0xFFF1F3FA),
    surfaceVariant = Color(0xFF252E48),
    onSurfaceVariant = Color(0xFFC3C9DA),
    errorContainer = Color(0xFF5B2427),
    onErrorContainer = Color(0xFFFFDAD8),
)

private val WakeMoveShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun WakeMoveTheme(
    themePreference: ThemePreference = ThemePreference.FOLLOW_SYSTEM,
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themePreference) {
        ThemePreference.FOLLOW_SYSTEM -> systemDark
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val context = LocalContext.current
    val colors = if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (darkTheme) {
        WakeMoveDarkColors
    } else {
        WakeMoveColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = WakeMoveTypography,
        shapes = WakeMoveShapes,
        content = content,
    )
}
