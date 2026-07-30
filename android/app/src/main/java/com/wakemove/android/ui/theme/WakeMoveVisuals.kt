package com.wakemove.android.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = this.size.minDimension * 0.028f
            val inset = stroke * 2
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
                center = Offset(this.size.width * 0.79f, this.size.height * 0.73f),
            )
            drawCircle(
                color = WakeMoveDawn.copy(alpha = 0.16f),
                radius = this.size.minDimension * 0.18f,
                center = Offset(this.size.width * 0.79f, this.size.height * 0.73f),
            )
        }
    }
}
