package com.wakemove.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val WakeMoveBackground = Color(0xFFFFF7ED)
val WakeMoveSurface = Color(0xFFFFFFFF)
val WakeMoveOrange = Color(0xFFF97316)
val WakeMoveCoral = Color(0xFFFB7185)
val WakeMoveText = Color(0xFF292524)

private val WakeMoveColors = lightColorScheme(
    primary = WakeMoveOrange,
    onPrimary = Color.White,
    secondary = WakeMoveCoral,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE4E6),
    onSecondaryContainer = Color(0xFF9F1239),
    background = WakeMoveBackground,
    onBackground = WakeMoveText,
    surface = WakeMoveSurface,
    onSurface = WakeMoveText,
    surfaceVariant = Color(0xFFFFE8D5),
    onSurfaceVariant = Color(0xFF57534E),
    error = Color(0xFFB91C1C),
)

private val WakeMoveShapes = Shapes(
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun WakeMoveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WakeMoveColors,
        typography = Typography(),
        shapes = WakeMoveShapes,
        content = content,
    )
}
