package com.hisabak.feature.dashboard.presentation

import com.hisabak.ui.icons.HugeIcons

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hisabak.R
import com.hisabak.core.common.Money
import com.hisabak.core.common.SummaryPeriod
import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.feature.category.presentation.CategoryStyle
import com.hisabak.feature.dashboard.domain.BrandShare
import com.hisabak.feature.dashboard.domain.CategoryOption
import com.hisabak.feature.dashboard.domain.CategoryShare
import com.hisabak.feature.dashboard.domain.DashboardSnapshot
import com.hisabak.feature.dashboard.domain.DayPoint
import com.hisabak.feature.dashboard.domain.MonthPoint
import com.hisabak.feature.dashboard.presentation.components.AreaLineChart
import com.hisabak.feature.dashboard.presentation.components.BarSparkline
import com.hisabak.feature.dashboard.presentation.components.DonutChart
import com.hisabak.feature.dashboard.presentation.components.DonutSlice
import com.hisabak.feature.dashboard.presentation.components.GroupedBarChart
import com.hisabak.ui.components.MoneyText
import com.hisabak.ui.components.PeriodChipRow
import com.hisabak.ui.components.animatedAmountMinor
import com.hisabak.ui.components.localizeDigits
import com.hisabak.ui.components.rememberIsArabic
import com.hisabak.ui.components.SectionHeader
import com.hisabak.ui.components.SkeletonCard
import com.hisabak.ui.components.SurfaceCard
import com.hisabak.ui.theme.HisabakTheme
import com.hisabak.ui.theme.HisabakType
import com.hisabak.ui.theme.LocalReducedMotion
import com.hisabak.ui.theme.Motion
import com.hisabak.ui.theme.PillShape
import com.hisabak.ui.theme.Sizing
import com.hisabak.ui.theme.Spacing
import com.hisabak.ui.theme.standardTween
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onPeriodChange: (SummaryPeriod) -> Unit,
    onShowUncategorized: () -> Unit,
    focusCategoryId: String? = null,
    onFocusConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val snap = state.snapshot
    if (snap == null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.pageMargin, vertical = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        ) {
            SkeletonCard(height = 168.dp) // net worth hero
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
                SkeletonCard(Modifier.weight(1f), height = 72.dp)
                SkeletonCard(Modifier.weight(1f), height = 72.dp)
                SkeletonCard(Modifier.weight(1f), height = 72.dp)
            }
            SkeletonCard(height = 120.dp)
            SkeletonCard(height = 120.dp)
        }
        return
    }

    var tab by rememberSaveable { mutableStateOf(DashboardTab.SUMMARY) }
    var expandedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    val tabDuration = if (LocalReducedMotion.current) 0 else Motion.Duration.Base
    val summaryListState = rememberLazyListState()
    val trendsListState = rememberLazyListState()
    val categoriesListState = rememberLazyListState()

    // A notification tap focuses a category: jump to the Categories tab and expand it.
    LaunchedEffect(focusCategoryId) {
        if (focusCategoryId != null) {
            tab = DashboardTab.CATEGORIES
            expandedCategoryId = focusCategoryId
            onFocusConsumed()
        }
    }

    Column(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.pageMargin)
                .padding(top = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            PeriodChipRow(selected = state.period, onSelect = onPeriodChange)
            DashboardTabs(selected = tab, onSelect = { tab = it })
        }
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(tween(tabDuration, easing = Motion.Easing.Standard)) +
                    slideInHorizontally(tween(tabDuration, easing = Motion.Easing.Standard)) { it / 12 })
                    .togetherWith(fadeOut(tween(tabDuration, easing = Motion.Easing.Standard)))
                    .using(SizeTransform(clip = false))
            },
            modifier = Modifier.weight(1f),
            label = "dashboardTab",
        ) { current ->
            when (current) {
                DashboardTab.SUMMARY -> SummaryTab(
                    snap = snap,
                    period = state.period,
                    listState = summaryListState,
                    onShowUncategorized = onShowUncategorized,
                    modifier = Modifier.fillMaxSize(),
                )
                DashboardTab.TRENDS -> TrendsTab(
                    snap = snap,
                    listState = trendsListState,
                    modifier = Modifier.fillMaxSize(),
                )
                DashboardTab.CATEGORIES -> CategoriesTab(
                    snap = snap,
                    period = state.period,
                    listState = categoriesListState,
                    expandedId = expandedCategoryId,
                    onToggleExpand = { id ->
                        expandedCategoryId = if (expandedCategoryId == id) null else id
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private enum class DashboardTab(val labelRes: Int) {
    SUMMARY(R.string.dashboard_tab_summary),
    TRENDS(R.string.dashboard_tab_trends),
    CATEGORIES(R.string.dashboard_tab_categories),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTabs(selected: DashboardTab, onSelect: (DashboardTab) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        DashboardTab.entries.forEachIndexed { index, tab ->
            SegmentedButton(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                shape = SegmentedButtonDefaults.itemShape(index, DashboardTab.entries.size),
            ) {
                Text(stringResource(tab.labelRes))
            }
        }
    }
}

// ── Summary tab: totals and their trajectory ───────────────────────────────────

@Composable
private fun SummaryTab(
    snap: DashboardSnapshot,
    period: SummaryPeriod,
    listState: LazyListState,
    onShowUncategorized: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = HisabakTheme.colors
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.pageMargin, end = Spacing.pageMargin, top = Spacing.s4, bottom = Spacing.s8),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        // ── Uncategorized nudge ─────────────────────────────────────────────
        if (snap.uncategorizedCount > 0) {
            item {
                UncategorizedBanner(
                    count = snap.uncategorizedCount,
                    total = snap.uncategorizedTotal,
                    onClick = onShowUncategorized,
                )
            }
        }

        // ── Net worth hero ──────────────────────────────────────────────────
        item {
            OverTimeCard(
                label = stringResource(R.string.dashboard_net_worth),
                money = snap.netWorth,
                trendPct = snap.netWorthTrendPct,
                trendPositiveIsGood = true,
                series = snap.netWorthSeries,
                period = period,
                lineColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                animateValue = true,
            )
        }

        // ── Cash / Savings / Investment pills ───────────────────────────────
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TotalPill(
                    label = stringResource(R.string.dashboard_cash),
                    money = snap.totalCash,
                    icon = { Icon(HugeIcons.AccountBalanceWallet, null, modifier = Modifier.size(16.dp)) },
                    bgColor = MaterialTheme.colorScheme.surfaceVariant,
                    fgColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TotalPill(
                    label = stringResource(R.string.category_type_savings),
                    money = snap.totalSavings,
                    icon = { Icon(HugeIcons.Savings, null, modifier = Modifier.size(16.dp)) },
                    bgColor = c.savingsSoft,
                    fgColor = c.savings,
                    modifier = Modifier.weight(1f),
                )
                TotalPill(
                    label = stringResource(R.string.category_type_invest_short),
                    money = snap.totalInvestment,
                    icon = { Icon(HugeIcons.TrendingUp, null, modifier = Modifier.size(16.dp)) },
                    bgColor = c.investmentSoft,
                    fgColor = c.investment,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ── Income / Expenses (net for the selected period) ─────────────────
        item {
            KpiCard(
                label = stringResource(R.string.category_type_income),
                money = snap.income,
                trendPct = snap.incomeTrendPct,
                trendPositiveIsGood = true,
                amountColor = c.income,
                sparklineValues = snap.incomeDaily.map { it.amountMinor / 100.0 },
                sparklineColor = c.income,
                sparklineLabels = dateLabels(snap.incomeDaily.map { it.day }, period),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            KpiCard(
                label = stringResource(R.string.category_type_expenses),
                money = snap.expense,
                trendPct = snap.expenseTrendPct,
                trendPositiveIsGood = false,
                amountColor = c.expense,
                sparklineValues = snap.expenseDaily.map { it.amountMinor / 100.0 },
                sparklineColor = c.expense,
                sparklineLabels = dateLabels(snap.expenseDaily.map { it.day }, period),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ── Income over time ────────────────────────────────────────────────
        item {
            OverTimeCard(
                label = stringResource(R.string.dashboard_income_over_time),
                money = snap.incomeTotal,
                trendPct = snap.incomeSeriesTrendPct,
                trendPositiveIsGood = true,
                series = snap.incomeSeries,
                period = period,
                lineColor = c.income,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ── Expense over time ───────────────────────────────────────────────
        item {
            OverTimeCard(
                label = stringResource(R.string.dashboard_expense_over_time),
                money = snap.expenseTotal,
                trendPct = snap.expenseSeriesTrendPct,
                trendPositiveIsGood = false,
                series = snap.expenseSeries,
                period = period,
                lineColor = c.expense,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ── Trends tab: breakdown by category, brand and over months ───────────────────

@Composable
private fun TrendsTab(
    snap: DashboardSnapshot,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val c = HisabakTheme.colors
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.pageMargin, end = Spacing.pageMargin, top = Spacing.s4, bottom = Spacing.s8),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        // ── Income & spending grouped bars ──────────────────────────────────
        item { SectionHeader(title = stringResource(R.string.dashboard_income_spending)) }
        item {
            val bars = monthlyPairs(snap.incomeDaily, snap.expenseDaily)
            if (bars.income.isNotEmpty()) {
                DashCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(bottom = Spacing.s4),
                    ) {
                        LegendDot(color = c.income, label = stringResource(R.string.category_type_income))
                        LegendDot(color = c.expense, label = stringResource(R.string.category_type_expenses))
                    }
                    GroupedBarChart(
                        incomeValues = bars.income,
                        expenseValues = bars.expense,
                        incomeColor = c.income,
                        expenseColor = c.expense,
                        modifier = Modifier.fillMaxWidth(),
                        xLabels = bars.labels,
                    )
                }
            }
        }

        // ── Expenses by category ────────────────────────────────────────────
        item { SectionHeader(title = stringResource(R.string.dashboard_expenses_by_category)) }
        item {
            CategoryDonutCard(
                shares = snap.expenseByCategory,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ── Top brands ──────────────────────────────────────────────────────
        item { SectionHeader(title = stringResource(R.string.dashboard_top_brands)) }
        item {
            BrandDonutCard(
                shares = snap.expenseByBrand,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ── Income sources ──────────────────────────────────────────────────
        if (snap.incomeByCategory.isNotEmpty()) {
            item { SectionHeader(title = stringResource(R.string.dashboard_income_sources)) }
            item {
                CategoryDonutCard(
                    shares = snap.incomeByCategory,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ── Hero / over-time chart card ────────────────────────────────────────────────

@Composable
private fun OverTimeCard(
    label: String,
    money: Money,
    trendPct: Double?,
    trendPositiveIsGood: Boolean,
    series: List<MonthPoint>,
    period: SummaryPeriod,
    lineColor: Color,
    modifier: Modifier = Modifier,
    animateValue: Boolean = false,
) {
    DashCard(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.s2))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MoneyText(
                amountMinor = if (animateValue) animatedAmountMinor(money.amountMinor) else money.amountMinor,
                style = HisabakType.amountHero,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (trendPct != null) {
                TrendBadge(pct = trendPct, positiveIsGood = trendPositiveIsGood)
            }
        }
        if (series.isNotEmpty()) {
            AreaLineChart(
                values = series.map { it.amountMinor / 100.0 },
                lineColor = lineColor,
                fillColor = lineColor.copy(alpha = 0.25f),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.cardGap, bottom = Spacing.s2),
                heightDp = 96.dp,
                xLabels = chartLabels(series, period),
            )
        }
    }
}

/** Per-point x-axis labels: day-of-month for month windows, month (with year when
 *  the window spans years) otherwise. */
private fun chartLabels(series: List<MonthPoint>, period: SummaryPeriod): List<String> =
    dateLabels(series.map { it.monthStart }, period)

private fun dateLabels(dates: List<LocalDate>, period: SummaryPeriod): List<String> {
    val daily = period == SummaryPeriod.CURRENT_MONTH || period == SummaryPeriod.LAST_MONTH
    val multiYear = dates.mapTo(HashSet()) { it.year }.size > 1
    val formatter = when {
        daily -> DateTimeFormatter.ofPattern("d MMM")
        multiYear -> DateTimeFormatter.ofPattern("MMM ''yy")
        else -> DateTimeFormatter.ofPattern("MMM")
    }
    return dates.map { it.format(formatter) }
}

// ── Stat pills ────────────────────────────────────────────────────────────────

@Composable
private fun TotalPill(
    label: String,
    money: Money,
    icon: @Composable () -> Unit,
    bgColor: Color,
    fgColor: Color,
    modifier: Modifier = Modifier,
) {
    SurfaceCard(modifier = modifier, contentPadding = Spacing.cardGap) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .background(bgColor, CircleShape)
                .padding(horizontal = Spacing.s3, vertical = Spacing.s2),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(16.dp)) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides fgColor,
                ) { icon() }
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = fgColor)
        }
        Spacer(Modifier.height(6.dp))
        MoneyText(
            amountMinor = money.amountMinor,
            style = HisabakType.amount,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── KPI card (income / expense) ───────────────────────────────────────────────

@Composable
private fun KpiCard(
    label: String,
    money: Money,
    trendPct: Double?,
    trendPositiveIsGood: Boolean,
    amountColor: Color,
    sparklineValues: List<Double>,
    sparklineColor: Color,
    sparklineLabels: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    DashCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (trendPct != null) {
                TrendBadge(pct = trendPct, positiveIsGood = trendPositiveIsGood)
            }
        }
        Spacer(Modifier.height(6.dp))
        MoneyText(
            amountMinor = money.amountMinor,
            style = HisabakType.amountLarge,
            color = amountColor,
        )
        if (sparklineValues.isNotEmpty()) {
            BarSparkline(
                values = sparklineValues,
                barColor = sparklineColor,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s3),
                heightDp = 64.dp,
                xLabels = sparklineLabels,
            )
        }
    }
}

// ── Trend badge ───────────────────────────────────────────────────────────────

@Composable
private fun TrendBadge(pct: Double, positiveIsGood: Boolean) {
    if (abs(pct) < 0.01) return
    val c = HisabakTheme.colors
    val isUp = pct > 0
    val good = if (positiveIsGood) isUp else !isUp
    val color = if (good) c.income else c.expense
    val icon = when {
        isUp -> HugeIcons.ArrowUpward
        else -> HugeIcons.ArrowDownward
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        Text(
            localizeDigits("%.0f%%".format(abs(pct)), rememberIsArabic()),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

// ── Donut + legend cards ──────────────────────────────────────────────────────

@Composable
private fun CategoryDonutCard(
    shares: List<CategoryShare>,
    modifier: Modifier = Modifier,
) {
    DashCard(modifier = modifier) {
        if (shares.isEmpty()) {
            Text(stringResource(R.string.dashboard_no_data), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@DashCard
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DonutChart(
                slices = shares.take(5).map { DonutSlice(it.pct, CategoryStyle.color(it.color)) },
                size = 112.dp,
                modifier = Modifier.size(112.dp),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.s3),
                modifier = Modifier.weight(1f),
            ) {
                shares.take(5).forEach { share ->
                    DonutLegendRow(
                        color = CategoryStyle.color(share.color),
                        label = share.name,
                        amount = share.amount,
                        pct = share.pct,
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandDonutCard(
    shares: List<BrandShare>,
    modifier: Modifier = Modifier,
) {
    DashCard(modifier = modifier) {
        if (shares.isEmpty()) {
            Text(stringResource(R.string.dashboard_no_data), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@DashCard
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DonutChart(
                slices = shares.take(5).map { DonutSlice(it.pct, CategoryStyle.color(it.color)) },
                size = 112.dp,
                modifier = Modifier.size(112.dp),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.s3),
                modifier = Modifier.weight(1f),
            ) {
                shares.take(5).forEach { share ->
                    DonutLegendRow(
                        color = CategoryStyle.color(share.color),
                        label = share.name,
                        amount = share.amount,
                        pct = share.pct,
                    )
                }
            }
        }
    }
}

@Composable
private fun DonutLegendRow(color: Color, label: String, amount: Money, pct: Double) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.size(Spacing.s3).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        MoneyText(
            amountMinor = amount.amountMinor,
            style = HisabakType.amount,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(Spacing.s2))
        Text(
            localizeDigits("%.0f%%".format(pct * 100), rememberIsArabic()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(30.dp),
            textAlign = TextAlign.End,
        )
    }
}

// ── Categories tab: per-category spend vs limit, expandable to a trend chart ────

@Composable
private fun CategoriesTab(
    snap: DashboardSnapshot,
    period: SummaryPeriod,
    listState: LazyListState,
    expandedId: String?,
    onToggleExpand: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = snap.categoryOptions
        .map { option ->
            val series = snap.trendByCategory[option.id].orEmpty()
            CategoryRowData(
                option = option,
                series = series,
                limitSeries = snap.limitByCategory[option.id].orEmpty(),
                spent = series.sumOf { it.amountMinor },
                prevTotal = snap.trendPrevTotalByCategory[option.id] ?: 0L,
            )
        }
        .filter { it.spent != 0L || periodLimit(it.limitSeries, period) != null }
        .sortedByDescending { abs(it.spent) }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.pageMargin, end = Spacing.pageMargin, top = Spacing.s4, bottom = Spacing.s8),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        if (rows.isEmpty() && snap.uncategorizedCount == 0) {
            item {
                Text(
                    stringResource(R.string.dashboard_no_category_activity),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.s8),
                )
            }
        }
        items(rows, key = { it.option.id.value }) { row ->
            CategoryLimitCard(
                row = row,
                period = period,
                expanded = expandedId == row.option.id.value,
                onToggle = { onToggleExpand(row.option.id.value) },
            )
        }
        if (snap.uncategorizedCount > 0) {
            item(key = UNCATEGORIZED_KEY) {
                UncategorizedCard(
                    total = snap.uncategorizedTotal,
                    count = snap.uncategorizedCount,
                    series = snap.uncategorizedSeries,
                    period = period,
                    expanded = expandedId == UNCATEGORIZED_KEY,
                    onToggle = { onToggleExpand(UNCATEGORIZED_KEY) },
                )
            }
        }
    }
}

private const val UNCATEGORIZED_KEY = "__uncategorized__"

private data class CategoryRowData(
    val option: CategoryOption,
    val series: List<DayPoint>,
    val limitSeries: List<Long?>,
    val spent: Long,
    val prevTotal: Long,
)

@Composable
private fun CategoryLimitCard(
    row: CategoryRowData,
    period: SummaryPeriod,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val color = CategoryStyle.color(row.option.color)
    val limit = periodLimit(row.limitSeries, period)
    val trendPct = row.prevTotal.takeIf { it != 0L }
        ?.let { (row.spent - it).toDouble() / it.toDouble() * 100.0 }
    val risingIsGood = row.option.type != CategoryType.EXPENSES

    DashCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Text(
                row.option.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            MoneyText(
                amountMinor = row.spent,
                style = HisabakType.amount,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                if (expanded) HugeIcons.ExpandLess else HugeIcons.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Sizing.iconSm),
            )
        }
        if (limit != null) {
            LimitProgressRow(spent = row.spent, limit = limit)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(standardTween()) + fadeIn(standardTween()),
            exit = shrinkVertically(standardTween()) + fadeOut(standardTween()),
        ) {
            Column {
                Spacer(Modifier.height(Spacing.s3))
                if (trendPct != null) {
                    TrendBadge(pct = trendPct, positiveIsGood = risingIsGood)
                }
                if (row.series.isNotEmpty() && row.spent != 0L) {
                    val chart = buildCategoryChart(row.series, row.limitSeries, period)
                    AreaLineChart(
                        values = chart.values,
                        lineColor = color,
                        fillColor = color.copy(alpha = 0.25f),
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.s2, bottom = Spacing.s2),
                        heightDp = 96.dp,
                        xLabels = dateLabels(row.series.map { it.day }, period),
                        overlayValues = chart.overlay,
                        overlayColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        stringResource(R.string.dashboard_no_activity),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.s4),
                    )
                }
            }
        }
    }
}

private data class CategoryChart(val values: List<Double>, val overlay: List<Double?>)

/** A single-month window shows cumulative spend vs a flat limit ceiling; a multi-month window
 *  shows monthly spend vs the (stepped, gap-aware) monthly limit. */
private fun buildCategoryChart(
    series: List<DayPoint>,
    limitSeries: List<Long?>,
    period: SummaryPeriod,
): CategoryChart {
    val singleMonth = period == SummaryPeriod.CURRENT_MONTH || period == SummaryPeriod.LAST_MONTH
    val values = if (singleMonth) {
        var running = 0L
        series.map { running += it.amountMinor; running / 100.0 }
    } else {
        series.map { it.amountMinor / 100.0 }
    }
    val ceiling = if (singleMonth) limitSeries.firstOrNull { it != null } else null
    val overlay = when {
        singleMonth && ceiling != null -> List(series.size) { ceiling / 100.0 }
        !singleMonth -> limitSeries.map { it?.let { v -> v / 100.0 } }
        else -> emptyList()
    }
    return CategoryChart(values, overlay)
}

/** The limit budget for the selected period: the month's limit for a single-month window, or the
 *  sum of each month's applicable limit for a multi-month window. Null if no limit applies. */
private fun periodLimit(limitSeries: List<Long?>, period: SummaryPeriod): Long? {
    val singleMonth = period == SummaryPeriod.CURRENT_MONTH || period == SummaryPeriod.LAST_MONTH
    return if (singleMonth) {
        limitSeries.firstOrNull { it != null }
    } else {
        limitSeries.filterNotNull().takeIf { it.isNotEmpty() }?.sum()
    }
}

@Composable
private fun UncategorizedBanner(count: Int, total: Money, onClick: () -> Unit) {
    DashCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
            Column(Modifier.weight(1f)) {
                Text(
                    pluralStringResource(R.plurals.dashboard_uncategorized_count, count, count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.dashboard_uncategorized_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MoneyText(
                amountMinor = total.amountMinor,
                style = HisabakType.amount,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                HugeIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Sizing.iconSm),
            )
        }
    }
}

@Composable
private fun UncategorizedCard(
    total: Money,
    count: Int,
    series: List<DayPoint>,
    period: SummaryPeriod,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    DashCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.dashboard_uncategorized),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    pluralStringResource(R.plurals.common_transaction_count, count, count),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                )
            }
            MoneyText(
                amountMinor = total.amountMinor,
                style = HisabakType.amount,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                if (expanded) HugeIcons.ExpandLess else HugeIcons.ExpandMore,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(Sizing.iconSm),
            )
        }
        AnimatedVisibility(
            visible = expanded && series.isNotEmpty() && total.amountMinor != 0L,
            enter = expandVertically(standardTween()) + fadeIn(standardTween()),
            exit = shrinkVertically(standardTween()) + fadeOut(standardTween()),
        ) {
            val chart = buildCategoryChart(series, emptyList(), period)
            AreaLineChart(
                values = chart.values,
                lineColor = color,
                fillColor = color.copy(alpha = 0.25f),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s3, bottom = Spacing.s2),
                heightDp = 96.dp,
                xLabels = dateLabels(series.map { it.day }, period),
            )
        }
    }
}

@Composable
private fun LimitProgressRow(spent: Long, limit: Long) {
    val c = HisabakTheme.colors
    val over = spent > limit
    val fraction = if (limit > 0) (spent.toDouble() / limit.toDouble()).toFloat().coerceIn(0f, 1f) else 0f
    val pct = if (limit > 0) (spent * 100.0 / limit).roundToInt() else 0
    val fillColor = if (over) c.expense else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(PillShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(Modifier.fillMaxWidth(fraction).height(6.dp).clip(PillShape).background(fillColor))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            Text(
                stringResource(R.string.dashboard_limit_pct_of, pct),
                style = MaterialTheme.typography.labelSmall,
                color = if (over) c.expense else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MoneyText(
                amountMinor = limit,
                style = MaterialTheme.typography.labelSmall,
                color = if (over) c.expense else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (over) {
                Text(
                    stringResource(R.string.dashboard_over_limit),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.expense,
                )
            }
        }
    }
}

// ── Shared card shell ─────────────────────────────────────────────────────────

@Composable
private fun DashCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    SurfaceCard(modifier = modifier, contentPadding = Spacing.cardPadding, content = content)
}

// ── Grouped bar legend dot ────────────────────────────────────────────────────

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(9.dp).background(color, androidx.compose.foundation.shape.RoundedCornerShape(2.dp)))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Data helpers ──────────────────────────────────────────────────────────────

private data class MonthlyBars(
    val income: List<Double>,
    val expense: List<Double>,
    val labels: List<String>,
)

/** Aggregate daily points into parallel monthly income/expense series across the period. */
private fun monthlyPairs(
    income: List<DayPoint>,
    expense: List<DayPoint>,
): MonthlyBars {
    val incomeByMonth = income
        .groupBy { it.day.withDayOfMonth(1) }
        .mapValues { (_, v) -> v.sumOf { it.amountMinor } / 100.0 }
    val expenseByMonth = expense
        .groupBy { it.day.withDayOfMonth(1) }
        .mapValues { (_, v) -> v.sumOf { it.amountMinor } / 100.0 }
    val months = (incomeByMonth.keys + expenseByMonth.keys)
        .toSortedSet()
        .toList()
    if (months.isEmpty()) return MonthlyBars(emptyList(), emptyList(), emptyList())
    val multiYear = months.mapTo(HashSet()) { it.year }.size > 1
    val formatter = DateTimeFormatter.ofPattern(if (multiYear) "MMM ''yy" else "MMM")
    return MonthlyBars(
        income = months.map { incomeByMonth[it] ?: 0.0 },
        expense = months.map { expenseByMonth[it] ?: 0.0 },
        labels = months.map { it.format(formatter) },
    )
}
