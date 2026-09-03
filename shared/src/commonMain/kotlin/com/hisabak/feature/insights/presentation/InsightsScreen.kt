package com.hisabak.feature.insights.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.hisabak.feature.insights.domain.Insight
import com.hisabak.feature.insights.domain.InsightType
import com.hisabak.feature.insights.domain.Severity
import com.hisabak.shared.resources.Res
import com.hisabak.shared.resources.insights_detail_largest
import com.hisabak.shared.resources.insights_detail_near_limit
import com.hisabak.shared.resources.insights_detail_new_spend
import com.hisabak.shared.resources.insights_detail_over_limit
import com.hisabak.shared.resources.insights_detail_savings_rate
import com.hisabak.shared.resources.insights_detail_savings_rate_delta
import com.hisabak.shared.resources.insights_detail_spend_down
import com.hisabak.shared.resources.insights_detail_spend_up
import com.hisabak.shared.resources.insights_detail_uncategorized
import com.hisabak.shared.resources.insights_empty_subtitle
import com.hisabak.shared.resources.insights_empty_title
import com.hisabak.shared.resources.insights_savings_title
import com.hisabak.shared.resources.insights_uncategorized_title
import com.hisabak.ui.components.EmptyStatePanel
import com.hisabak.ui.components.IconTile
import com.hisabak.ui.components.MoneyText
import com.hisabak.ui.components.SurfaceCard
import com.hisabak.ui.components.iconForKey
import com.hisabak.ui.components.localizeDigits
import com.hisabak.ui.components.localizedFormatArg
import com.hisabak.ui.components.rememberIsArabic
import com.hisabak.ui.components.tintPairForColor
import com.hisabak.ui.icons.HugeIcons
import com.hisabak.ui.theme.HisabakTheme
import com.hisabak.ui.theme.HisabakType
import com.hisabak.ui.theme.Spacing
import kotlin.math.abs
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun InsightsScreen(
    state: InsightsUiState,
    onInsightClick: (Insight) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isLoading && state.insights.isEmpty()) {
        EmptyStatePanel(
            title = stringResource(Res.string.insights_empty_title),
            subtitle = stringResource(Res.string.insights_empty_subtitle),
            icon = HugeIcons.Insights,
            modifier = modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.pageMargin,
            end = Spacing.pageMargin,
            top = Spacing.s4,
            bottom = Spacing.s8,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        items(state.insights, key = { it.id }) { insight ->
            SurfaceCard(onClick = { onInsightClick(insight) }) {
                InsightRow(insight)
            }
        }
    }
}

/**
 * One finding as a row — glyph tile, title, one-line detail, and the figure on the trailing edge.
 * Shared by the dashboard's Review card and the insights screen so the copy lives in one place.
 */
@Composable
fun InsightRow(insight: Insight, modifier: Modifier = Modifier) {
    val c = HisabakTheme.colors
    val category = insight.category
    val (tileBg, tileFg) = when {
        category != null -> tintPairForColor(category.color)
        insight.type == InsightType.SavingsRate && insight.severity == Severity.Warning -> c.expenseSoft to c.expense
        // The Summary tab's savings pill is blue with the bank glyph; the row matches it.
        insight.type == InsightType.SavingsRate -> c.savingsSoft to c.savings
        else -> c.warningSoft to c.warning
    }
    val icon = when {
        category != null -> iconForKey(category.icon)
        insight.type == InsightType.SavingsRate -> HugeIcons.Bank
        else -> HugeIcons.Tag
    }
    // Severity reads through the figure, not a stripe or a badge: the amount is what the user
    // will act on, so it is what carries the colour.
    val amountColor: Color = when (insight.severity) {
        Severity.Warning -> c.expense
        Severity.Notice -> c.warning
        Severity.Info -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        IconTile(icon = icon, background = tileBg, foreground = tileFg)
        Column(Modifier.weight(1f)) {
            Text(
                text = insightTitle(insight),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = insightDetail(insight),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        insight.amountMinor?.let { amount ->
            Spacer(Modifier.width(Spacing.s2))
            MoneyText(amountMinor = amount, style = HisabakType.amount, color = amountColor)
        }
    }
}

@Composable
private fun insightTitle(insight: Insight): String = when {
    insight.category != null -> insight.category.name
    insight.type == InsightType.SavingsRate -> stringResource(Res.string.insights_savings_title)
    else -> stringResource(Res.string.insights_uncategorized_title)
}

@Composable
private fun insightDetail(insight: Insight): String {
    val arabic = rememberIsArabic()
    fun pct(fraction: Double) = localizeDigits("${(fraction * 100).roundToInt()}%", arabic)
    fun absPct(percent: Double) = localizeDigits("${abs(percent).roundToInt()}%", arabic)
    fun signedPoints(points: Double): String {
        val sign = if (points >= 0) "+" else "−"
        return localizeDigits("$sign${abs(points).roundToInt()}", arabic)
    }
    return when (insight.type) {
        InsightType.OverLimit -> stringResource(Res.string.insights_detail_over_limit)
        InsightType.NearLimit -> stringResource(Res.string.insights_detail_near_limit, pct(insight.share ?: 0.0))
        InsightType.SpendUp -> stringResource(Res.string.insights_detail_spend_up, absPct(insight.deltaPct ?: 0.0))
        InsightType.SpendDown -> stringResource(Res.string.insights_detail_spend_down, absPct(insight.deltaPct ?: 0.0))
        InsightType.NewSpend -> stringResource(Res.string.insights_detail_new_spend)
        InsightType.LargestCategory -> stringResource(Res.string.insights_detail_largest, pct(insight.share ?: 0.0))
        InsightType.SavingsRate -> {
            val rate = pct(insight.share ?: 0.0)
            val delta = insight.deltaPct
            if (delta == null) {
                stringResource(Res.string.insights_detail_savings_rate, rate)
            } else {
                stringResource(Res.string.insights_detail_savings_rate_delta, rate, signedPoints(delta))
            }
        }
        InsightType.Uncategorized -> {
            val n = insight.count ?: 0
            pluralStringResource(Res.plurals.insights_detail_uncategorized, n, localizedFormatArg(n))
        }
    }
}
