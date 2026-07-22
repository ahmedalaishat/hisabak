package com.hisabak.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hisabak.ui.theme.HisabakTheme
import com.hisabak.ui.theme.PillShape
import com.hisabak.ui.theme.Spacing

enum class BadgeTone { Neutral, Income, Expense, Savings, Investment, Success, Warning, Danger, Info }

@Composable
fun Badge(
    label: String,
    tone: BadgeTone = BadgeTone.Neutral,
    dot: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = HisabakTheme.colors
    val (bg, fg) = when (tone) {
        BadgeTone.Neutral    -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        BadgeTone.Income     -> colors.incomeSoft to colors.income
        BadgeTone.Expense    -> colors.expenseSoft to colors.expense
        BadgeTone.Savings    -> colors.savingsSoft to colors.savings
        BadgeTone.Investment -> colors.investmentSoft to colors.investment
        BadgeTone.Success    -> colors.incomeSoft to colors.income
        BadgeTone.Warning    -> colors.warningSoft to colors.warning
        BadgeTone.Danger     -> colors.expenseSoft to colors.expense
        BadgeTone.Info       -> colors.infoSoft to colors.info
    }

    Row(
        modifier = modifier
            .clip(PillShape)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot) {
            Box(
                modifier = Modifier
                    .size(Spacing.s3)
                    .clip(CircleShape)
                    .background(fg),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
        )
    }
}
