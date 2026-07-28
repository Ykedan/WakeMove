package com.wakemove.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val WakeMoveBackground = Color(0xFFFFF8F0)
val WakeMoveSurface = Color(0xFFFFFFFF)
val WakeMoveSunrise = Color(0xFFFF7A1A)
val WakeMoveSunlight = Color(0xFFFFC45C)
val WakeMovePeach = Color(0xFFFFE8D2)
val WakeMoveText = Color(0xFF2F261F)
val WakeMoveMutedText = Color(0xFF75675C)
val WakeMoveErrorContainer = Color(0xFFFFE8E6)

private val WakeMoveColors = lightColorScheme(
    primary = WakeMoveSunrise,
    onPrimary = WakeMoveText,
    primaryContainer = WakeMovePeach,
    onPrimaryContainer = WakeMoveText,
    secondary = WakeMoveSunlight,
    onSecondary = WakeMoveText,
    secondaryContainer = WakeMovePeach,
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
        fontSize = 48.sp,
        lineHeight = 56.sp,
        fontWeight = FontWeight.Bold,
    ),
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)

private val WakeMoveShapes = Shapes(
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun WakeMoveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WakeMoveColors,
        typography = WakeMoveTypography,
        shapes = WakeMoveShapes,
        content = content,
    )
}
