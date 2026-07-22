package com.hisabak.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.hisabak.ui.theme.HisabakTheme
import com.hisabak.ui.theme.HisabakType
import com.hisabak.ui.theme.Spacing

/**
 * Recent-activity / recent-brand row: leading icon slot, two-line text column,
 * trailing content (typically an amount + small label). Uses [SurfaceCard] as
 * the container so padding, border, and background stay consistent with the
 * rest of the design.
 */
@Composable
fun ListRow(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    SurfaceCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = Spacing.cardGap,
        onClick = onClick,
    ) {
        ListRowContent(title = title, subtitle = subtitle, leading = leading, trailing = trailing)
    }
}

/**
 * The inner leading / two-line text / trailing layout of [ListRow] without the [SurfaceCard]
 * container — for stacking several rows inside a single shared card (e.g. day-grouped lists),
 * with dividers between them. Pass a clickable + padded [modifier] for per-row tap targets.
 */
@Composable
fun ListRowContent(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
    }
}

@Composable
fun TrailingAmount(
    amount: String,
    caption: String? = null,
    tone: AmountTone = AmountTone.Neutral,
) {
    val c = HisabakTheme.colors
    val color = when (tone) {
        AmountTone.Income     -> c.income
        AmountTone.Expense    -> c.expense
        AmountTone.Savings    -> c.savings
        AmountTone.Investment -> c.investment
        AmountTone.Auto,
        AmountTone.Neutral    -> MaterialTheme.colorScheme.onSurface
    }
    Column(horizontalAlignment = Alignment.End) {
        Text(
            amount,
            style = HisabakType.amount,
            color = color,
            maxLines = 1,
        )
        if (!caption.isNullOrBlank()) {
            Text(
                caption,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
