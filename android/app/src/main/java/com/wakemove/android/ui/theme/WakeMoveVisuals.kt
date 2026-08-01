package com.wakemove.android.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * WakeMove's signature mark: the warm wake point crossing a cool night orbit.
 * It is intentionally drawn in Compose so it stays crisp without a generic stock illustration.
 */
@Composable
fun WakeOrbitMark(
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    dark: Boolean = false,
) {
    val orbit = if (dark) Color.White.copy(alpha = 0.22f) else WakeMoveBlue.copy(alpha = 0.22f)
    val strongOrbit = if (dark) WakeMoveSky else WakeMoveBlue
    val transition = rememberInfiniteTransition(label = "wake-orbit")
    val wakePointAngle by transition.animateFloat(
        initialValue = 38f,
        targetValue = 398f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wake-point-angle",
    )
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = this.size.minDimension * 0.028f
            val inset = stroke * 2
            val orbitRadius = (this.size.minDimension - inset * 2) / 2f
            val angleRadians = Math.toRadians(wakePointAngle.toDouble())
            val wakePoint = Offset(
                x = center.x + orbitRadius * cos(angleRadians).toFloat(),
                y = center.y + orbitRadius * sin(angleRadians).toFloat(),
            )
            drawArc(
                color = orbit,
                startAngle = 198f,
                sweepAngle = 290f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(this.size.width - inset * 2, this.size.height - inset * 2),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = strongOrbit,
                startAngle = 210f,
                sweepAngle = 118f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(this.size.width - inset * 2, this.size.height - inset * 2),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawCircle(
                color = WakeMoveDawn,
                radius = this.size.minDimension * 0.115f,
                center = wakePoint,
            )
            drawCircle(
                color = WakeMoveDawn.copy(alpha = 0.16f),
                radius = this.size.minDimension * 0.18f,
                center = wakePoint,
            )
        }
    }
}
