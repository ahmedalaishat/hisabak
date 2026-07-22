package com.hisabak.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import com.hisabak.ui.theme.PillShape
import com.hisabak.ui.theme.Spacing

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
            .padding(horizontal = Spacing.pageMargin, vertical = 10.dp),
    )
}

@Composable
fun ColoredFilterChip(
    label: String,
    colorKey: String?,
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
    val dotColor = if (colorKey != null) tintPairForColor(colorKey).second else Color.Transparent

    Row(
        modifier = Modifier
            .hisabakPressable(shape = PillShape, background = bg, onClick = onClick)
            .padding(horizontal = if (colorKey != null) 10.dp else 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (colorKey != null) {
            Box(
                modifier = Modifier
                    .size(Spacing.s3)
                    .background(dotColor, CircleShape),
            )
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
            .padding(start = 10.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
        )
    }
}
