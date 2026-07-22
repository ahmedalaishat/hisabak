package com.hisabak.ui.components

import com.hisabak.ui.icons.HugeIcons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.hisabak.ui.theme.Sizing
import com.hisabak.ui.theme.Spacing

/**
 * Highlighted "MOST USED" banner card — a tinted [SurfaceCard] with a star label, a
 * colored [IconTile], a title, and an optional trailing slot. Shared by the brand and
 * category list screens, which differ only in the trailing element (badge vs. chip).
 */
@Composable
fun MostUsedCard(
    icon: ImageVector,
    colorKey: String?,
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val (bg, fg) = tintPairForColor(colorKey)
    SurfaceCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(
                icon = icon,
                size = Sizing.controlHeight,
                iconSize = Sizing.icon,
                background = bg,
                foreground = fg,
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    Icon(
                        HugeIcons.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        "MOST USED",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(Spacing.s1))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            trailing?.invoke()
        }
    }
}
