package com.hisabak.feature.transaction.presentation.list

import com.hisabak.ui.icons.HugeIcons

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import com.hisabak.ui.components.SkeletonCard
import com.hisabak.ui.components.SkeletonRowList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hisabak.shared.resources.*
import com.hisabak.ui.components.localizedFormatArg
import com.hisabak.core.common.Money
import com.hisabak.core.common.SummaryPeriod
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.feature.category.presentation.CategoryStyle
import com.hisabak.feature.transaction.domain.TransactionId
import com.hisabak.ui.components.AmountText
import com.hisabak.ui.components.AmountTone
import com.hisabak.ui.components.CircleIconTile
import com.hisabak.ui.components.PeriodChipRow
import com.hisabak.ui.components.EmptyStatePanel
import com.hisabak.ui.components.ExpensesStatCard
import com.hisabak.ui.components.IncomeStatCard
import com.hisabak.ui.components.ListRowContent
import com.hisabak.ui.components.ProgressBar
import com.hisabak.ui.components.SearchField
import com.hisabak.ui.components.SurfaceCard
import com.hisabak.ui.components.iconForKey
import com.hisabak.ui.components.tintPairForColor
import com.hisabak.ui.theme.HisabakTheme
import com.hisabak.ui.theme.PillShape
import com.hisabak.ui.theme.Sizing
import com.hisabak.ui.theme.Spacing
import com.hisabak.ui.format.LocalDateFormatter
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TransactionListScreen(
    state: TransactionListUiState,
    onSearchChange: (String) -> Unit,
    onPeriodChange: (SummaryPeriod) -> Unit,
    onBrandFilterChange: (BrandId?) -> Unit,
    onCategoryFilterChange: (CategoryId?) -> Unit,
    onDateRangeChange: (DateRangeFilter) -> Unit,
    onClearFilters: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (TransactionId) -> Unit,
) {
    if (state.isLoading) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.pageMargin, vertical = Spacing.s3),
            verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
                SkeletonCard(Modifier.weight(1f))
                SkeletonCard(Modifier.weight(1f))
            }
            SkeletonRowList(count = 6)
        }
        return
    }

    // The period scopes the summary cards; brand / category / date-range scope the list.
    var openFilter by remember { mutableStateOf<FilterTarget?>(null) }

    // Rows arrive newest-first; group them by day for date-headed cards (LinkedHashMap keeps order).
    val zone = remember { TimeZone.currentSystemDefault() }
    val dayGroups = remember(state.rows) {
        state.rows.groupBy { it.occurredAt.toLocalDateTime(zone).date }.entries.toList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.pageMargin,
            end = Spacing.pageMargin,
            top = Spacing.s3,
            bottom = Spacing.s10 + Spacing.s7, // clear the Add FAB
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                Text(
                    text = stringResource(Res.string.transaction_summary),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                PeriodChipRow(selected = state.period, onSelect = onPeriodChange)
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap),
                ) {
                    IncomeStatCard(
                        amountMinor = state.summaryIncome,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    ExpensesStatCard(
                        amountMinor = state.summaryExpenses,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                IncomeRatioBar(
                    incomeMinor = state.summaryIncome,
                    expensesMinor = state.summaryExpenses,
                )
            }
        }

        item {
            SearchField(
                value = state.search,
                onValueChange = onSearchChange,
                placeholder = stringResource(Res.string.transaction_search_placeholder),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterPill(
                    label = state.selectedCategoryName ?: stringResource(Res.string.common_category),
                    active = state.categoryFilter != null,
                    onClick = { openFilter = FilterTarget.CATEGORY },
                )
                FilterPill(
                    label = state.selectedBrandName ?: stringResource(Res.string.common_brand),
                    active = state.brandFilter != null,
                    onClick = { openFilter = FilterTarget.BRAND },
                )
                FilterPill(
                    label = if (state.dateRange == DateRangeFilter.ALL) stringResource(Res.string.common_date) else stringResource(state.dateRange.labelRes),
                    active = state.dateRange != DateRangeFilter.ALL,
                    onClick = { openFilter = FilterTarget.DATE },
                )
                if (state.hasActiveFilters) {
                    Text(
                        text = stringResource(Res.string.action_clear),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(onClick = onClearFilters)
                            .padding(horizontal = Spacing.s2, vertical = Spacing.s2),
                    )
                }
            }
        }

        if (state.rows.isEmpty()) {
            val filtered = state.hasActiveFilters || state.search.isNotBlank()
            item {
                if (filtered) {
                    EmptyStatePanel(
                        title = stringResource(Res.string.transaction_empty_filtered_title),
                        subtitle = stringResource(Res.string.transaction_empty_filtered_subtitle),
                        icon = HugeIcons.ReceiptLong,
                        actionLabel = stringResource(if (state.hasActiveFilters) Res.string.action_clear_filters else Res.string.transaction_add),
                        onAction = if (state.hasActiveFilters) onClearFilters else onAdd,
                    )
                } else {
                    EmptyStatePanel(
                        title = stringResource(Res.string.transaction_empty_title),
                        subtitle = stringResource(Res.string.transaction_empty_subtitle),
                        icon = HugeIcons.ReceiptLong,
                        actionLabel = stringResource(Res.string.transaction_add),
                        onAction = onAdd,
                    )
                }
            }
        } else {
            dayGroups.forEach { (date, rows) ->
                item(key = "day-$date") {
                    Column(Modifier.animateItem()) {
                        DayHeader(date)
                        Spacer(Modifier.height(Spacing.s2))
                        SurfaceCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                            rows.forEachIndexed { index, row ->
                                TransactionRowContent(row = row, onClick = { onEdit(row.id) })
                                if (index < rows.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier.padding(horizontal = Spacing.cardGap),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    when (openFilter) {
        FilterTarget.CATEGORY -> FilterSelectSheet(
            title = stringResource(Res.string.transaction_filter_category),
            entries = state.categoryOptions.map { FilterEntry(it.id.value, it.name, it.color) },
            selectedId = state.categoryFilter?.value,
            onSelect = { id -> onCategoryFilterChange(id?.let(::CategoryId)); openFilter = null },
            onDismiss = { openFilter = null },
        )
        FilterTarget.BRAND -> FilterSelectSheet(
            title = stringResource(Res.string.transaction_filter_brand),
            entries = state.brandOptions.map { FilterEntry(it.id.value, it.name, null) },
            selectedId = state.brandFilter?.value,
            onSelect = { id -> onBrandFilterChange(id?.let(::BrandId)); openFilter = null },
            onDismiss = { openFilter = null },
        )
        FilterTarget.DATE -> FilterSelectSheet(
            title = stringResource(Res.string.transaction_filter_date),
            entries = DateRangeFilter.entries
                .filter { it != DateRangeFilter.ALL }
                .map { FilterEntry(it.name, stringResource(it.labelRes), null) },
            selectedId = state.dateRange.takeIf { it != DateRangeFilter.ALL }?.name,
            onSelect = { id ->
                onDateRangeChange(id?.let { DateRangeFilter.valueOf(it) } ?: DateRangeFilter.ALL)
                openFilter = null
            },
            onDismiss = { openFilter = null },
            allLabel = stringResource(DateRangeFilter.ALL.labelRes),
        )
        null -> Unit
    }
}

private enum class FilterTarget { CATEGORY, BRAND, DATE }

private data class FilterEntry(val id: String, val label: String, val color: String?)

@Composable
private fun FilterPill(label: String, active: Boolean, onClick: () -> Unit) {
    val bg = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .clip(PillShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.s4, vertical = Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
        Icon(
            HugeIcons.ExpandMore,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(Sizing.iconSm),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSelectSheet(
    title: String,
    entries: List<FilterEntry>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    allLabel: String = stringResource(Res.string.common_all),
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.s7),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = Spacing.pageMargin, vertical = Spacing.s4),
            )
            FilterSheetRow(label = allLabel, colorKey = null, selected = selectedId == null) { onSelect(null) }
            entries.forEach { entry ->
                FilterSheetRow(
                    label = entry.label,
                    colorKey = entry.color,
                    selected = selectedId == entry.id,
                ) { onSelect(entry.id) }
            }
        }
    }
}

@Composable
private fun FilterSheetRow(
    label: String,
    colorKey: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.pageMargin, vertical = Spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        if (colorKey != null) {
            Box(Modifier.size(10.dp).background(CategoryStyle.color(colorKey), CircleShape))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                HugeIcons.Check,
                contentDescription = stringResource(Res.string.common_selected),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Sizing.icon),
            )
        }
    }
}

// ---- internals -----------------------------------------------------------

/** A slim bar visualising the period's income share of total money flow. */
@Composable
private fun IncomeRatioBar(incomeMinor: Long, expensesMinor: Long) {
    val total = incomeMinor + expensesMinor
    if (total <= 0L) return
    val ratio = (incomeMinor.toDouble() / total.toDouble()).toFloat()
    val pct = (ratio * 100).roundToInt()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        ProgressBar(progress = ratio, color = HisabakTheme.colors.income)
        Text(
            text = stringResource(Res.string.transaction_income_ratio, localizedFormatArg(pct)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayHeader(date: LocalDate) {
    Text(
        text = dayLabel(date).uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.s1),
    )
}

@Composable
private fun dayLabel(date: LocalDate): String {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    return when (date) {
        today -> stringResource(Res.string.time_today)
        today.minus(1, DateTimeUnit.DAY) -> stringResource(Res.string.time_yesterday)
        else -> {
            val formatter = LocalDateFormatter.current
            if (date.year == today.year) formatter.monthDay(date) else formatter.fullDate(date)
        }
    }
}

/** A single transaction row without its own card — for stacking inside a day-grouped card. */
@Composable
private fun TransactionRowContent(
    row: TransactionRow,
    onClick: () -> Unit,
) {
    val tone = when (row.categoryType) {
        CategoryType.INCOME -> AmountTone.Income
        CategoryType.EXPENSES -> AmountTone.Expense
        CategoryType.SAVINGS -> AmountTone.Savings
        CategoryType.INVESTMENT -> AmountTone.Investment
        // Uncategorized (e.g. just captured from SMS) has no income/expense meaning yet —
        // show it neutral and unsigned rather than masquerading as green income.
        null -> AmountTone.Neutral
    }
    val (bg, fg) = tintPairForColor(row.categoryColor)
    val amountValue = row.amount.amountMinor.toMajorDouble()
    val dateLabel = formatRelative(row.occurredAt)
    val subtitle = listOfNotNull(
        row.categoryName,
        row.note?.takeIf { it.isNotBlank() },
    ).joinToString(" · ").takeIf { it.isNotBlank() }

    ListRowContent(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.cardGap, vertical = Spacing.s3),
        title = row.brandName,
        subtitle = subtitle,
        leading = {
            CircleIconTile(
                icon = iconForKey(row.categoryIcon),
                background = bg,
                foreground = fg,
            )
        },
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                // Signed value: a savings/investment withdrawal is stored negative and must read −.
                AmountText(
                    value = amountValue,
                    currency = row.amount.currency.code,
                    showSign = true,
                    tone = tone,
                    size = 14.sp,
                )
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

private fun Long.toMajor(): String {
    val major = this / 100
    val minor = abs(this % 100)
    return "$major.${minor.toString().padStart(2, '0')}"
}

private fun Long.toMajorDouble(): Double = this / 100.0

internal fun formatMoneyMajor(amountMinor: Long, currency: String): String {
    val prefix = if (currency.isBlank()) "" else "$currency "
    return prefix + amountMinor.toMajor()
}

internal fun formatSignedAmount(money: Money, positive: Boolean): String {
    val sign = if (positive) "+" else "-"
    return "$sign${money.currency.code} ${abs(money.amountMinor).toMajor()}"
}

internal fun formatMoney(money: Money): String {
    val major = money.amountMinor / 100
    val minor = abs(money.amountMinor % 100)
    val sign = if (money.amountMinor < 0) "-" else ""
    return "$sign${money.currency.code} $major.${minor.toString().padStart(2, '0')}"
}

@Composable
internal fun formatDate(instant: Instant): String = LocalDateFormatter.current.fullDate(instant)

@Composable
private fun formatRelative(instant: Instant): String {
    val now = Clock.System.now()
    val diff = now - instant
    return when {
        diff.isNegative() -> formatDate(instant)
        diff.inWholeHours < 1 -> stringResource(Res.string.time_minutes_ago, localizedFormatArg(diff.inWholeMinutes.coerceAtLeast(1).toInt()))
        diff.inWholeHours < 24 -> stringResource(Res.string.time_hours_ago, localizedFormatArg(diff.inWholeHours.toInt()))
        diff.inWholeDays == 1L -> stringResource(Res.string.time_yesterday)
        diff.inWholeDays < 7 -> stringResource(Res.string.time_days_ago, localizedFormatArg(diff.inWholeDays.toInt()))
        else -> formatDate(instant)
    }
}
