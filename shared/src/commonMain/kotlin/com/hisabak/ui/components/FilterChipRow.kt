package com.hisabak.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyHorizontalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.hisabak.ui.theme.PillShape
import com.hisabak.ui.theme.Spacing

private val ChipVerticalPadding = 10.dp
private val ChipIconSize = 18.dp

/** Floor for a 1.5-weight stroke glyph: below this the stroke thins and detail muddies. */
private val ChipGlyphSize = 16.dp

@Composable
fun <T> FilterChipRow(
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.pageMargin),
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        contentPadding = contentPadding,
    ) {
        items(options) { option ->
            val (label, value) = option
            FilterPill(
                label = label,
                selected = value == selected,
                onClick = { onSelect(value) },
            )
        }
    }
}

@Composable
fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "chipBg",
    )
    val fg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "chipFg",
    )
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier = Modifier
            .hisabakPressable(shape = PillShape, background = bg, onClick = onClick)
            .padding(horizontal = Spacing.pageMargin, vertical = ChipVerticalPadding),
    )
}

@Composable
fun ColoredFilterChip(
    label: String,
    colorKey: String?,
    selected: Boolean,
    onClick: () -> Unit,
    /**
     * Category icon key. Given one, the chip shows that glyph instead of a plain dot — colours
     * repeat across categories (the wheel only holds so many), but glyphs don't, so the icon is
     * what actually tells two same-coloured chips apart.
     */
    iconKey: String? = null,
) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "chipBg",
    )
    val fg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "chipFg",
    )
    val dotColor = if (colorKey != null) tintPairForColor(colorKey).second else Color.Transparent

    Row(
        modifier = Modifier
            .hisabakPressable(shape = PillShape, background = bg, onClick = onClick)
            .padding(horizontal = if (colorKey != null) 10.dp else 18.dp, vertical = ChipVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (colorKey != null) {
            if (iconKey != null) {
                Icon(
                    imageVector = iconForKey(iconKey),
                    contentDescription = null,
                    tint = dotColor,
                    modifier = Modifier.size(ChipGlyphSize),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(Spacing.s3)
                        .background(dotColor, CircleShape),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
        )
    }
}

@Composable
fun LeadingIconChip(
    label: String,
    leadingIcon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "chipBg",
    )
    val fg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "chipFg",
    )

    Row(
        modifier = Modifier
            .hisabakPressable(shape = PillShape, background = bg, onClick = onClick)
            .padding(start = 10.dp, end = 14.dp, top = ChipVerticalPadding, bottom = ChipVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(ChipIconSize),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
        )
    }
}

/**
 * Lanes are earned by length: up to 4 chips stay a single row, up to 8 split across two,
 * anything longer fills three.
 */
fun chipLaneCount(chipCount: Int): Int = when {
    chipCount > 8 -> 3
    chipCount > 4 -> 2
    else -> 1
}

/**
 * A chip row that wraps into [chipLaneCount] lanes and still scrolls sideways — the answer to
 * lists (brands, categories) too long to read in one row. The band is sized from the type scale
 * rather than a fixed height, so it grows with the user's font-size setting instead of clipping.
 */
@Composable
fun ChipLaneGrid(
    chipCount: Int,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: LazyStaggeredGridScope.() -> Unit,
) {
    val lanes = chipLaneCount(chipCount)
    val chipHeight = with(LocalDensity.current) {
        maxOf(MaterialTheme.typography.labelMedium.lineHeight.toDp(), ChipIconSize) +
            ChipVerticalPadding * 2
    }
    LazyHorizontalStaggeredGrid(
        rows = StaggeredGridCells.Fixed(lanes),
        modifier = modifier.height(chipHeight * lanes + Spacing.s3 * (lanes - 1)),
        horizontalItemSpacing = Spacing.s3,
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        contentPadding = contentPadding,
        content = content,
    )
}
