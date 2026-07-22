package com.hisabak.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hisabak.ui.theme.Spacing

data class SegmentOption<T>(
    val value: T,
    val label: String,
    val tone: BadgeTone = BadgeTone.Neutral,
)

@Composable
fun <T> SegmentedControl(
    options: List<SegmentOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = com.hisabak.ui.theme.HisabakTheme.colors
    val trackShape = RoundedCornerShape(10.dp)
    val segmentShape = RoundedCornerShape(7.dp)

    Row(
        modifier = modifier
            .clip(trackShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        options.forEach { option ->
            val isSelected = option.value == selected

            val (segBg, segFg) = if (isSelected) {
                when (option.tone) {
                    BadgeTone.Neutral    -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
                    BadgeTone.Income     -> colors.incomeSoft to colors.income
                    BadgeTone.Expense    -> colors.expenseSoft to colors.expense
                    BadgeTone.Savings    -> colors.savingsSoft to colors.savings
                    BadgeTone.Investment -> colors.investmentSoft to colors.investment
                    BadgeTone.Success    -> colors.incomeSoft to colors.income
                    BadgeTone.Warning    -> colors.warningSoft to colors.warning
                    BadgeTone.Danger     -> colors.expenseSoft to colors.expense
                    BadgeTone.Info       -> colors.infoSoft to colors.info
                }
            } else {
                androidx.compose.ui.graphics.Color.Transparent to MaterialTheme.colorScheme.onSurfaceVariant
            }
            val animBg by animateColorAsState(segBg, label = "segBg")
            val animFg by animateColorAsState(segFg, label = "segFg")

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(Spacing.s9)
                    .hisabakPressable(shape = segmentShape, background = animBg) { onSelect(option.value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = animFg,
                )
            }
        }
    }
}
