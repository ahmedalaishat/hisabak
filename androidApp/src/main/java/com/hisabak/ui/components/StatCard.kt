package com.hisabak.ui.components

import com.hisabak.ui.icons.HugeIcons

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hisabak.R
import com.hisabak.ui.theme.HisabakTheme
import com.hisabak.ui.theme.Motion
import com.hisabak.ui.theme.PillShape
import com.hisabak.ui.theme.Spacing
import com.hisabak.ui.theme.standardTween

/**
 * Small KPI card used in 2-up grids: tinted icon tile + label + big value,
 * with an optional thin progress bar beneath.
 */
@Composable
fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    accent: StatAccent = StatAccent.Positive,
    currencySymbol: Boolean = false,
) {
    val c = HisabakTheme.colors
    val bg = when (accent) {
        StatAccent.Positive -> c.incomeSoft
        StatAccent.Negative -> c.expenseSoft
    }
    val fg = when (accent) {
        StatAccent.Positive -> MaterialTheme.colorScheme.primary
        StatAccent.Negative -> MaterialTheme.colorScheme.error
    }
    SurfaceCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            IconTile(
                icon = icon,
                size = 28.dp,
                iconSize = 16.dp,
                background = bg,
                foreground = fg,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.s2))
        if (currencySymbol) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DirhamGlyph(
                    size = MaterialTheme.typography.titleLarge.fontSize * 0.8f,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(Spacing.s1))
                Text(
                    value,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        } else {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (progress != null) {
            Spacer(Modifier.height(Spacing.s3))
            ProgressBar(progress = progress, color = fg)
        }
    }
}

enum class StatAccent { Positive, Negative }

@Composable
fun ProgressBar(
    progress: Float,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = clamped,
        animationSpec = standardTween(Motion.Duration.Slow),
        label = "progressFill",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, PillShape),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(4.dp)
                .background(color, PillShape),
        )
    }
}

// Convenience wrappers that match the two most common call sites in the design.
@Composable
fun IncomeStatCard(
    value: String,
    progress: Float? = null,
    modifier: Modifier = Modifier,
    currencySymbol: Boolean = false,
) = StatCard(
    label = stringResource(R.string.category_type_income),
    value = value,
    icon = HugeIcons.TrendingUp,
    accent = StatAccent.Positive,
    progress = progress,
    modifier = modifier,
    currencySymbol = currencySymbol,
)

@Composable
fun ExpensesStatCard(
    value: String,
    progress: Float? = null,
    modifier: Modifier = Modifier,
    currencySymbol: Boolean = false,
) = StatCard(
    label = stringResource(R.string.category_type_expenses),
    value = value,
    icon = HugeIcons.TrendingDown,
    accent = StatAccent.Negative,
    progress = progress,
    modifier = modifier,
    currencySymbol = currencySymbol,
)
