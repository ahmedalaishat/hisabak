package com.hisabak.feature.dashboard.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hisabak.ui.theme.LocalReducedMotion
import com.hisabak.ui.theme.Motion

data class DonutSlice(val value: Double, val color: Color)

@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    strokeWidthDp: Int = 14,
    size: Dp = 80.dp,
) {
    val reduced = LocalReducedMotion.current
    // Sweep the ring in (and re-sweep when the slices change, e.g. switching period) for a little
    // life. Snaps straight to full under reduced motion.
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(slices, reduced) {
        if (reduced) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(Motion.Duration.Slow, easing = Motion.Easing.Standard),
            )
        }
    }
    Canvas(modifier = modifier) {
        val stroke = strokeWidthDp.dp.toPx()
        val sizePx = size.toPx()
        val diameter = sizePx - stroke
        val topLeft = Offset((this.size.width - diameter) / 2f, (this.size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)
        val total = slices.sumOf { it.value }
        if (total <= 0.0) {
            drawArc(
                color = Color.LightGray.copy(alpha = 0.3f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            return@Canvas
        }
        // Round the segment ends and leave a small gap between them so they read as separate pills.
        val roundStroke = Stroke(width = stroke, cap = StrokeCap.Round)
        val gap = if (slices.size > 1) 4f else 0f
        var start = -90f
        slices.forEach { slice ->
            val full = (slice.value / total * 360.0).toFloat()
            val sweep = (full - gap).coerceAtLeast(0f) * progress.value
            if (sweep > 0f) {
                drawArc(
                    color = slice.color,
                    startAngle = start + gap / 2f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = roundStroke,
                )
            }
            start += full
        }
    }
}
