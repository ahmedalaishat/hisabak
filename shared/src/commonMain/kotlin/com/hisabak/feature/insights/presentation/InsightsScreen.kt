package com.hisabak.feature.insights.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.hisabak.feature.insights.domain.Insight
import com.hisabak.feature.insights.domain.InsightType
import com.hisabak.feature.insights.domain.InsightsSummary
import com.hisabak.feature.insights.domain.Severity
import com.hisabak.feature.insights.domain.ai.NarrativeInsight
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
import com.hisabak.shared.resources.insights_section_ai
import com.hisabak.shared.resources.insights_section_findings
import com.hisabak.shared.resources.insights_narrative_footer
import com.hisabak.shared.resources.insights_narrative_loading
import com.hisabak.shared.resources.insights_narrative_unavailable
import com.hisabak.shared.resources.insights_ask_accept
import com.hisabak.shared.resources.insights_ask_body
import com.hisabak.shared.resources.insights_ask_title
import com.hisabak.shared.resources.insights_shared_action
import com.hisabak.shared.resources.insights_shared_expense
import com.hisabak.shared.resources.insights_shared_income
import com.hisabak.shared.resources.insights_shared_intro
import com.hisabak.shared.resources.insights_shared_limit
import com.hisabak.shared.resources.insights_shared_prior
import com.hisabak.shared.resources.insights_shared_title
import com.hisabak.shared.resources.insights_suggest_limit
import com.hisabak.shared.resources.common_done
import com.hisabak.shared.resources.period_all_time
import com.hisabak.shared.resources.period_last_month
import com.hisabak.shared.resources.period_last_year
import com.hisabak.shared.resources.period_this_month
import com.hisabak.shared.resources.period_this_year
import com.hisabak.ui.components.LeadingIconChip
import com.hisabak.ui.components.NoticeCard
import com.hisabak.ui.components.NoticeTone
import com.hisabak.ui.components.PeriodChipRow
import com.hisabak.ui.components.PrimaryPillButton
import com.hisabak.ui.components.SkeletonBox
import androidx.compose.foundation.shape.RoundedCornerShape
import com.hisabak.ui.components.compactAmountMinor
import com.hisabak.ui.components.exactAmount
import com.hisabak.ui.components.EmptyStatePanel
import com.hisabak.ui.components.IconTile
import com.hisabak.ui.components.MoneyText
import com.hisabak.ui.components.SectionHeader
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
import com.hisabak.core.common.SummaryPeriod
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.abs
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun InsightsScreen(
    state: InsightsUiState,
    onInsightClick: (Insight) -> Unit,
    onNarrativeClick: (NarrativeInsight) -> Unit,
    onSuggestionClick: (NarrativeInsight) -> Unit,
    onIntent: (InsightsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.showShared && state.summary != null) {
        SharedSummaryDialog(summary = state.summary, onDismiss = { onIntent(InsightsIntent.HideShared) })
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
        // The chips live here too: re-scoping the review should not mean a trip back to the
        // dashboard, and an empty period must still offer a way to another one.
        item(key = "period") {
            PeriodChipRow(
                selected = state.period,
                onSelect = { onIntent(InsightsIntent.PeriodChanged(it)) },
                modifier = Modifier.padding(bottom = Spacing.s1),
            )
        }
        if (!state.isLoading && state.insights.isEmpty()) {
            item(key = "empty") {
                EmptyStatePanel(
                    title = stringResource(Res.string.insights_empty_title),
                    subtitle = stringResource(Res.string.insights_empty_subtitle),
                    icon = HugeIcons.Insights,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.s8),
                )
            }
            return@LazyColumn
        }
        // The AI layer sits above the findings it explains — an explanation is read before its
        // evidence — and never replaces them: the deterministic list below is complete on its own.
        // Each is a titled section once both are on screen, so the reader knows which words came
        // from a model and which from arithmetic.
        narrativeItems(state.narrative, onNarrativeClick, onSuggestionClick, onIntent)
        item(key = "findings:header") {
            SectionHeader(
                title = stringResource(Res.string.insights_section_findings),
                modifier = Modifier.padding(top = Spacing.s2),
            )
        }
        items(state.insights, key = { it.id }) { insight ->
            SurfaceCard(onClick = { onInsightClick(insight) }) {
                InsightRow(insight)
            }
        }
    }
}

private fun LazyListScope.narrativeItems(
    narrative: NarrativeUi,
    onNarrativeClick: (NarrativeInsight) -> Unit,
    onSuggestionClick: (NarrativeInsight) -> Unit,
    onIntent: (InsightsIntent) -> Unit,
) {
    when (narrative) {
        NarrativeUi.Hidden -> Unit
        // The ask is self-titled; the section header appears once there is a section.
        NarrativeUi.Ask -> item(key = "ai:ask") { NarrativeAskCard(onIntent) }
        NarrativeUi.Loading -> {
            narrativeHeader(onIntent)
            item(key = "ai:loading") { NarrativeLoadingCards() }
        }
        is NarrativeUi.Ready -> narrativeCards(narrative.items, onNarrativeClick, onSuggestionClick, onIntent)
        NarrativeUi.Unavailable -> {
            narrativeHeader(onIntent)
            item(key = "ai:unavailable") {
                NoticeCard(text = stringResource(Res.string.insights_narrative_unavailable), tone = NoticeTone.Info)
            }
        }
    }
}

/** "See what's shared" rides the header: it is about the section, not any one card. */
private fun LazyListScope.narrativeHeader(onIntent: (InsightsIntent) -> Unit) {
    item(key = "ai:header") {
        SectionHeader(
            title = stringResource(Res.string.insights_section_ai),
            actionLabel = stringResource(Res.string.insights_shared_action),
            onAction = { onIntent(InsightsIntent.ShowShared) },
        )
    }
}

private fun LazyListScope.narrativeCards(
    cards: List<NarrativeInsight>,
    onNarrativeClick: (NarrativeInsight) -> Unit,
    onSuggestionClick: (NarrativeInsight) -> Unit,
    onIntent: (InsightsIntent) -> Unit,
) {
    if (cards.isEmpty()) return
    narrativeHeader(onIntent)
    items(cards, key = { it.id }) { item ->
        NarrativeCard(
            item = item,
            onClick = { onNarrativeClick(item) },
            onSuggestion = { onSuggestionClick(item) },
        )
    }
    item(key = "ai:footer") { NarrativeFooter() }
}

/**
 * One AI item under the "Explained by AI" header: the glyph tile says what it is about (the category's own icon, or the idea glyph for a period-wide item), and the optional chip
 * is the only action — confirm-first, opening the editor prefilled rather than writing anything.
 */
@Composable
private fun NarrativeCard(item: NarrativeInsight, onClick: () -> Unit, onSuggestion: () -> Unit) {
    val c = HisabakTheme.colors
    val category = item.category
    val (tileBg, tileFg) = if (category != null) tintPairForColor(category.color) else c.infoSoft to c.info
    SurfaceCard(onClick = category?.let { { onClick() } }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            IconTile(
                icon = if (category != null) iconForKey(category.icon) else HugeIcons.Idea,
                background = tileBg,
                foreground = tileFg,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.headline,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (item.detail.isNotBlank()) {
                    Spacer(Modifier.height(Spacing.s1))
                    Text(
                        text = item.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val limit = item.suggestedLimitMinor
        if (category != null && limit != null) {
            Spacer(Modifier.height(Spacing.s3))
            LeadingIconChip(
                label = stringResource(Res.string.insights_suggest_limit, compactAmountMinor(limit, rememberIsArabic())),
                leadingIcon = HugeIcons.Tag,
                selected = false,
                onClick = onSuggestion,
            )
        }
    }
}

@Composable
private fun NarrativeFooter() {
    Text(
        text = stringResource(Res.string.insights_narrative_footer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s1),
    )
}

/**
 * Loading as the shape of what is coming: two skeleton cards with the narrative card's footprint
 * (tile, headline, two lines of detail), so the list does not jump when the real cards land, and a
 * status line on the first so the wait reads as work rather than a stall. The shimmer respects
 * reduced motion via [SkeletonBox].
 */
@Composable
private fun NarrativeLoadingCards() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        SurfaceCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Spacing.s4),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(Res.string.insights_narrative_loading),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.s3))
            SkeletonNarrativeBody(headlineFraction = 0.7f)
        }
        SurfaceCard { SkeletonNarrativeBody(headlineFraction = 0.55f) }
    }
}

@Composable
private fun SkeletonNarrativeBody(headlineFraction: Float) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3), verticalAlignment = Alignment.Top) {
        SkeletonBox(Modifier.size(Spacing.s9), height = Spacing.s9, shape = RoundedCornerShape(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            SkeletonBox(Modifier.fillMaxWidth(headlineFraction), height = 14.dp)
            SkeletonBox(Modifier.fillMaxWidth(), height = 10.dp)
            SkeletonBox(Modifier.fillMaxWidth(0.8f), height = 10.dp)
        }
    }
}

/**
 * The ask. Consent is per request: nothing is sent until "Explain with AI" is tapped, and "See
 * what's shared" beside it shows the exact figures that tap would send. No switch to remember, so
 * nothing to revoke — the next send is the next tap.
 */
@Composable
private fun NarrativeAskCard(onIntent: (InsightsIntent) -> Unit) {
    val c = HisabakTheme.colors
    SurfaceCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3), verticalAlignment = Alignment.Top) {
            IconTile(icon = HugeIcons.Idea, background = c.infoSoft, foreground = c.info)
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.insights_ask_title),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.s1))
                Text(
                    text = stringResource(Res.string.insights_ask_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Spacing.s3))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrimaryPillButton(
                text = stringResource(Res.string.insights_ask_accept),
                onClick = { onIntent(InsightsIntent.RequestNarrative) },
                vertical = Spacing.s2,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onIntent(InsightsIntent.ShowShared) }) {
                Text(stringResource(Res.string.insights_shared_action))
            }
        }
    }
}

/** The payload, verbatim: the same fields `InsightsSummary.toRequestDto` sends, and nothing else. */
@Composable
private fun SharedSummaryDialog(summary: InsightsSummary, onDismiss: () -> Unit) {
    val arabic = rememberIsArabic()
    fun money(minor: Long?) = if (minor == null) "—" else exactAmount(minor / 100.0, arabic)
    val periodLabel = stringResource(
        when (summary.period) {
            SummaryPeriod.CURRENT_MONTH -> Res.string.period_this_month
            SummaryPeriod.LAST_MONTH -> Res.string.period_last_month
            SummaryPeriod.CURRENT_YEAR -> Res.string.period_this_year
            SummaryPeriod.LAST_YEAR -> Res.string.period_last_year
            SummaryPeriod.ALL -> Res.string.period_all_time
        },
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_done)) } },
        title = { Text(stringResource(Res.string.insights_shared_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                Text(
                    text = stringResource(Res.string.insights_shared_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SharedRow(periodLabel, "")
                SharedRow(
                    stringResource(Res.string.insights_shared_income),
                    money(summary.incomeMinor),
                    stringResource(Res.string.insights_shared_prior, money(summary.priorIncomeMinor)),
                )
                SharedRow(
                    stringResource(Res.string.insights_shared_expense),
                    money(summary.expenseMinor),
                    stringResource(Res.string.insights_shared_prior, money(summary.priorExpenseMinor)),
                )
                summary.categories.forEach { c ->
                    SharedRow(
                        c.name,
                        money(c.spentMinor),
                        listOfNotNull(
                            stringResource(Res.string.insights_shared_prior, money(c.priorMinor)),
                            c.limitMinor?.let { stringResource(Res.string.insights_shared_limit, money(it)) },
                        ).joinToString(" · "),
                    )
                }
                SharedRow(
                    stringResource(Res.string.insights_uncategorized_title),
                    money(summary.uncategorizedMinor),
                    pluralStringResource(
                        Res.plurals.insights_detail_uncategorized,
                        summary.uncategorizedCount,
                        localizedFormatArg(summary.uncategorizedCount),
                    ),
                )
            }
        },
    )
}

@Composable
private fun SharedRow(label: String, value: String, sub: String? = null) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = value, style = HisabakType.amount, color = MaterialTheme.colorScheme.onSurface)
        }
        if (!sub.isNullOrBlank()) {
            Text(text = sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
