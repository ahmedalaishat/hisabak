package com.hisabak.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hisabak.ui.theme.LocalReducedMotion
import com.hisabak.ui.theme.PillShape

/*
 * Loading skeletons — a calm, slow shimmer sweep over muted placeholder blocks.
 * Under reduced motion the shimmer is dropped and a static muted block is shown.
 */

@Composable
private fun shimmerBrush(): Brush {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    if (LocalReducedMotion.current) {
        return Brush.linearGradient(listOf(base, base))
    }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -300f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerX",
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = androidx.compose.ui.geometry.Offset(x, 0f),
        end = androidx.compose.ui.geometry.Offset(x + 300f, 0f),
    )
}

/** A single shimmering placeholder block. */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    shape: Shape = PillShape,
) {
    Box(
        modifier
            .height(height)
            .clip(shape)
            .background(shimmerBrush()),
    )
}

/** Placeholder for a list row: leading circle + two stacked lines + trailing amount. */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SkeletonBox(Modifier.size(40.dp), height = 40.dp, shape = CircleShape)
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SkeletonBox(Modifier.fillMaxWidth(0.5f), height = 14.dp)
            SkeletonBox(Modifier.fillMaxWidth(0.3f), height = 12.dp)
        }
        SkeletonBox(Modifier.width(64.dp), height = 14.dp)
    }
}

/** A muted placeholder card matching the default SurfaceCard footprint. */
@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier,
    height: Dp = 88.dp,
) {
    SkeletonBox(
        modifier = modifier.fillMaxWidth(),
        height = height,
        shape = MaterialTheme.shapes.medium,
    )
}

/** Vertical stack of [count] skeleton rows — drop-in for a loading list. */
@Composable
fun SkeletonRowList(
    count: Int = 6,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        repeat(count) {
            SkeletonRow()
            Spacer(Modifier.height(4.dp))
        }
    }
}
