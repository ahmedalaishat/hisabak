package com.hisabak.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.hisabak.ui.icons.HugeIcons
import com.hisabak.ui.theme.HisabakTheme
import com.hisabak.ui.theme.Sizing
import com.hisabak.ui.theme.Spacing

/**
 * NoticeCard — inline feedback in a tinted card, following the banner pattern (soft tone
 * background, no border, leading stroke icon in the accent color).
 *
 *   Info    — neutral guidance ("also matches 3 messages", "already exists")
 *   Warning — caution that doesn't block ("may be too generic")
 *   Error   — blocks the action ("tag the amount")
 */
enum class NoticeTone { Info, Warning, Error }

@Composable
fun NoticeCard(
    text: String,
    tone: NoticeTone,
    modifier: Modifier = Modifier,
) {
    val colors = HisabakTheme.colors
    val (background, accent) = when (tone) {
        NoticeTone.Info -> colors.infoSoft to colors.info
        NoticeTone.Warning -> colors.warningSoft to colors.warning
        NoticeTone.Error -> colors.expenseSoft to colors.expense
    }
    val icon = when (tone) {
        NoticeTone.Info -> HugeIcons.ErrorOutline
        NoticeTone.Warning -> HugeIcons.PriorityHigh
        NoticeTone.Error -> HugeIcons.ErrorOutline
    }
    SurfaceCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = background,
        borderColor = Color.Transparent,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(Sizing.iconSm),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
