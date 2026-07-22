package com.hisabak.feature.dashboard.domain.usecase

import com.hisabak.core.common.Clock
import com.hisabak.core.common.Currency
import com.hisabak.core.common.Money
import com.hisabak.core.common.SummaryPeriod
import com.hisabak.feature.brand.domain.Brand
import com.hisabak.feature.brand.domain.usecase.ObserveBrandsUseCase
import com.hisabak.feature.category.domain.Category
import com.hisabak.feature.category.domain.CategoryLimit
import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.feature.category.domain.effectiveFor
import com.hisabak.feature.category.domain.usecase.ObserveCategoriesUseCase
import com.hisabak.feature.category.domain.usecase.ObserveCategoryLimitsUseCase
import com.hisabak.feature.dashboard.domain.BrandShare
import com.hisabak.feature.dashboard.domain.CategoryOption
import com.hisabak.feature.dashboard.domain.CategoryShare
import com.hisabak.feature.dashboard.domain.DashboardSnapshot
import com.hisabak.feature.dashboard.domain.DayPoint
import com.hisabak.feature.dashboard.domain.MonthPoint
import com.hisabak.feature.transaction.domain.Transaction
import com.hisabak.feature.transaction.domain.usecase.ObserveTransactionsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class GetDashboardMetricsUseCase(
    private val observeTransactions: ObserveTransactionsUseCase,
    private val observeCategories: ObserveCategoriesUseCase,
    private val observeBrands: ObserveBrandsUseCase,
    private val observeCategoryLimits: ObserveCategoryLimitsUseCase,
    private val currency: Currency,
    private val clock: Clock,
) {
    operator fun invoke(period: Flow<SummaryPeriod>): Flow<DashboardSnapshot> =
        combine(
            observeTransactions(),
            observeCategories(),
            observeBrands(),
            observeCategoryLimits(),
            period,
        ) { txs, cats, brands, limits, p ->
            compute(txs, cats, brands, limits, p)
        }

    private fun compute(
        transactions: List<Transaction>,
        categories: List<Category>,
        brands: List<Brand>,
        limits: List<CategoryLimit>,
        period: SummaryPeriod,
    ): DashboardSnapshot {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.ofInstant(clock.now(), zone)

        val catById = categories.associateBy { it.id }
        val brandById = brands.associateBy { it.id }

        fun typeOf(tx: Transaction): CategoryType? =
            brandById[tx.brandId]?.categoryId?.let { catById[it]?.type }

        val range = period.instantRange(today, zone)
        fun inPeriod(tx: Transaction): Boolean =
            range == null || (!tx.occurredAt.isBefore(range.first) && tx.occurredAt.isBefore(range.second))
        fun upToEnd(tx: Transaction): Boolean =
            range == null || tx.occurredAt.isBefore(range.second)

        val periodTxs = transactions.filter(::inPeriod)
        val cumulativeTxs = transactions.filter(::upToEnd)

        fun sumType(list: List<Transaction>, type: CategoryType): Long =
            list.filter { typeOf(it) == type }.sumOf { it.amount.amountMinor }

        val signedNetWorth: (Transaction) -> Long = { tx ->
            when (typeOf(tx)) {
                CategoryType.INCOME -> tx.amount.amountMinor
                CategoryType.EXPENSES -> -tx.amount.amountMinor
                else -> 0L
            }
        }

        // Cumulative-to-end-of-period balances (the wealth snapshot).
        val incomeCumulative = sumType(cumulativeTxs, CategoryType.INCOME)
        val expenseCumulative = sumType(cumulativeTxs, CategoryType.EXPENSES)
        val savingsCumulative = sumType(cumulativeTxs, CategoryType.SAVINGS)
        val investmentCumulative = sumType(cumulativeTxs, CategoryType.INVESTMENT)
        val netWorth = incomeCumulative - expenseCumulative
        val totalCash = netWorth - savingsCumulative - investmentCumulative

        // Flow within the selected period.
        val income = sumType(periodTxs, CategoryType.INCOME)
        val expense = sumType(periodTxs, CategoryType.EXPENSES)

        // Trend versus the equal-length window immediately before this one.
        val prevRange = period.previousInstantRange(today, zone)
        val prevTxs = prevRange?.let { (start, end) ->
            transactions.filter { !it.occurredAt.isBefore(start) && it.occurredAt.isBefore(end) }
        }.orEmpty()
        val incomeTrendPct = pctChange(sumType(prevTxs, CategoryType.INCOME), income)
        val expenseTrendPct = pctChange(sumType(prevTxs, CategoryType.EXPENSES), expense)

        // Cumulative-to-date running series for the hero / over-time charts. Each carries the
        // balance accumulated before the period start, so they read as running totals.
        val beforePeriod = transactions.filter { range != null && it.occurredAt.isBefore(range.first) }
        val openingNetWorth = beforePeriod.sumOf(signedNetWorth)
        val openingIncome = beforePeriod.filter { typeOf(it) == CategoryType.INCOME }.sumOf { it.amount.amountMinor }
        val openingExpense = beforePeriod.filter { typeOf(it) == CategoryType.EXPENSES }.sumOf { it.amount.amountMinor }

        val netWorthSeries = cumulativeSeries(periodTxs, signedNetWorth, openingNetWorth, zone, period, today)
        val incomeSeries = cumulativeSeries(
            periodTxs.filter { typeOf(it) == CategoryType.INCOME },
            { it.amount.amountMinor }, openingIncome, zone, period, today,
        )
        val expenseSeries = cumulativeSeries(
            periodTxs.filter { typeOf(it) == CategoryType.EXPENSES },
            { it.amount.amountMinor }, openingExpense, zone, period, today,
        )

        fun seriesTrend(series: List<MonthPoint>): Double? =
            if (series.size >= 2) pctChange(series.first().amountMinor, series.last().amountMinor) else null
        val netWorthTrendPct = seriesTrend(netWorthSeries)
        val incomeSeriesTrendPct = seriesTrend(incomeSeries)
        val expenseSeriesTrendPct = seriesTrend(expenseSeries)

        // Per-bucket flow series for the small sparklines and grouped bars.
        val incomeDaily = flowSeries(periodTxs.filter { typeOf(it) == CategoryType.INCOME }, zone, period, today)
        val expenseDaily = flowSeries(periodTxs.filter { typeOf(it) == CategoryType.EXPENSES }, zone, period, today)

        val incomeByCategory = breakdown(
            transactions = periodTxs.filter { typeOf(it) == CategoryType.INCOME },
            categoryOf = { brandById[it.brandId]?.categoryId?.let(catById::get) },
            currency = currency,
        )
        val expenseByCategory = breakdown(
            transactions = periodTxs.filter { typeOf(it) == CategoryType.EXPENSES },
            categoryOf = { brandById[it.brandId]?.categoryId?.let(catById::get) },
            currency = currency,
        )

        val expenseByBrand = brandBreakdown(
            transactions = periodTxs.filter { typeOf(it) == CategoryType.EXPENSES },
            brandById = brandById,
            catById = catById,
            currency = currency,
        )
        val topBrand = expenseByBrand.firstOrNull()
        val topBrandTrend = if (topBrand != null) {
            buildMonthlySum(transactions.filter { it.brandId == topBrand.id }, zone)
        } else emptyList()

        // Category trends follow the selected period, with the same granularity as the
        // over-time charts (daily for month windows, monthly otherwise).
        val categoryOf: (Transaction) -> Category? = { brandById[it.brandId]?.categoryId?.let(catById::get) }
        val periodTxsByCategory = periodTxs.groupBy { categoryOf(it)?.id }
        val prevTxsByCategory = prevTxs.groupBy { categoryOf(it)?.id }
        val trendByCategory = categories.associate { cat ->
            cat.id to flowSeries(periodTxsByCategory[cat.id].orEmpty(), zone, period, today)
        }
        val trendPrevTotalByCategory = categories.associate { cat ->
            cat.id to prevTxsByCategory[cat.id].orEmpty().sumOf { it.amount.amountMinor }
        }
        // The applicable monthly limit for each bucket of a category's trend (null = no limit then).
        val limitByCategory = categories.associate { cat ->
            cat.id to trendByCategory[cat.id].orEmpty().map { point ->
                limits.effectiveFor(cat.id, YearMonth.from(point.day))?.amountMinor
            }
        }

        // Transactions whose brand has no category have no type, so they sit outside income/expense
        // and net worth. Surface them as their own bucket instead of silently dropping them.
        val uncategorizedTxs = periodTxs.filter { typeOf(it) == null }
        val categoryOptions = categories
            .sortedBy { it.name.lowercase() }
            .map { CategoryOption(id = it.id, name = it.name, color = it.color, type = it.type) }

        return DashboardSnapshot(
            netWorth = Money(netWorth, currency),
            netWorthSeries = netWorthSeries,
            netWorthTrendPct = netWorthTrendPct,
            totalCash = Money(totalCash, currency),
            totalSavings = Money(savingsCumulative, currency),
            totalInvestment = Money(investmentCumulative, currency),
            income = Money(income, currency),
            incomeTrendPct = incomeTrendPct,
            incomeTotal = Money(incomeCumulative, currency),
            incomeSeries = incomeSeries,
            incomeSeriesTrendPct = incomeSeriesTrendPct,
            expense = Money(expense, currency),
            expenseTrendPct = expenseTrendPct,
            expenseTotal = Money(expenseCumulative, currency),
            expenseSeries = expenseSeries,
            expenseSeriesTrendPct = expenseSeriesTrendPct,
            incomeDaily = incomeDaily,
            expenseDaily = expenseDaily,
            incomeByCategory = incomeByCategory,
            expenseByCategory = expenseByCategory,
            categoryOptions = categoryOptions,
            trendByCategory = trendByCategory,
            trendPrevTotalByCategory = trendPrevTotalByCategory,
            limitByCategory = limitByCategory,
            uncategorizedTotal = Money(uncategorizedTxs.sumOf { it.amount.amountMinor }, currency),
            uncategorizedCount = uncategorizedTxs.size,
            uncategorizedSeries = flowSeries(uncategorizedTxs, zone, period, today),
            expenseByBrand = expenseByBrand,
            topBrandTrend = topBrandTrend,
            topBrandName = topBrand?.name,
        )
    }

    private fun pctChange(prev: Long, current: Long): Double? {
        if (prev == 0L) return null
        return ((current - prev).toDouble() / prev.toDouble()) * 100.0
    }

    /** Ordered bucket start dates for [period]: per-day for month windows, per-month otherwise. */
    private fun bucketDates(
        period: SummaryPeriod,
        today: LocalDate,
        bucketTxs: List<Transaction>,
        zone: ZoneId,
    ): List<LocalDate> = when (period) {
        SummaryPeriod.CURRENT_MONTH -> {
            val month = YearMonth.from(today)
            (1..today.dayOfMonth).map { month.atDay(it) }
        }
        SummaryPeriod.LAST_MONTH -> {
            val month = YearMonth.from(today).minusMonths(1)
            (1..month.lengthOfMonth()).map { month.atDay(it) }
        }
        SummaryPeriod.CURRENT_YEAR -> (1..today.monthValue).map { LocalDate.of(today.year, it, 1) }
        SummaryPeriod.LAST_YEAR -> (1..12).map { LocalDate.of(today.year - 1, it, 1) }
        SummaryPeriod.ALL -> {
            if (bucketTxs.isEmpty()) {
                emptyList()
            } else {
                val months = bucketTxs.map { YearMonth.from(LocalDate.ofInstant(it.occurredAt, zone)) }
                val out = mutableListOf<LocalDate>()
                var cursor = months.min()
                val end = months.max()
                while (!cursor.isAfter(end)) {
                    out += cursor.atDay(1)
                    cursor = cursor.plusMonths(1)
                }
                out
            }
        }
    }

    private fun bucketKey(tx: Transaction, period: SummaryPeriod, zone: ZoneId): LocalDate {
        val day = LocalDate.ofInstant(tx.occurredAt, zone)
        return if (period == SummaryPeriod.CURRENT_MONTH || period == SummaryPeriod.LAST_MONTH) {
            day
        } else {
            day.withDayOfMonth(1)
        }
    }

    private fun cumulativeSeries(
        bucketTxs: List<Transaction>,
        valueOf: (Transaction) -> Long,
        opening: Long,
        zone: ZoneId,
        period: SummaryPeriod,
        today: LocalDate,
    ): List<MonthPoint> {
        val dates = bucketDates(period, today, bucketTxs, zone)
        val byBucket = bucketTxs.groupBy { bucketKey(it, period, zone) }
            .mapValues { (_, list) -> list.sumOf(valueOf) }
        var running = opening
        return dates.map { date ->
            running += byBucket[date] ?: 0L
            MonthPoint(date, running)
        }
    }

    private fun flowSeries(
        typeTxs: List<Transaction>,
        zone: ZoneId,
        period: SummaryPeriod,
        today: LocalDate,
    ): List<DayPoint> {
        val dates = bucketDates(period, today, typeTxs, zone)
        val byBucket = typeTxs.groupBy { bucketKey(it, period, zone) }
            .mapValues { (_, list) -> list.sumOf { it.amount.amountMinor } }
        return dates.map { DayPoint(it, byBucket[it] ?: 0L) }
    }

    private fun buildMonthlySum(transactions: List<Transaction>, zone: ZoneId): List<MonthPoint> {
        if (transactions.isEmpty()) return emptyList()
        val byMonth = transactions.groupBy { YearMonth.from(LocalDate.ofInstant(it.occurredAt, zone)) }
            .mapValues { (_, list) -> list.sumOf { it.amount.amountMinor } }
        val sorted = byMonth.keys.sorted()
        val start = sorted.first()
        val end = sorted.last()
        val points = mutableListOf<MonthPoint>()
        var cursor = start
        while (!cursor.isAfter(end)) {
            points += MonthPoint(cursor.atDay(1), byMonth[cursor] ?: 0L)
            cursor = cursor.plusMonths(1)
        }
        return points
    }

    private fun breakdown(
        transactions: List<Transaction>,
        categoryOf: (Transaction) -> Category?,
        currency: Currency,
    ): List<CategoryShare> {
        val grouped = transactions.groupBy { categoryOf(it) }
        val total = grouped.values.sumOf { list -> list.sumOf { it.amount.amountMinor } }
        if (total == 0L) return emptyList()
        return grouped.mapNotNull { (cat, list) ->
            if (cat == null) return@mapNotNull null
            val sum = list.sumOf { it.amount.amountMinor }
            CategoryShare(
                id = cat.id,
                name = cat.name,
                color = cat.color,
                amount = Money(sum, currency),
                pct = sum.toDouble() / total.toDouble(),
            )
        }.sortedByDescending { it.amount.amountMinor }
    }

    private fun brandBreakdown(
        transactions: List<Transaction>,
        brandById: Map<com.hisabak.feature.brand.domain.BrandId, Brand>,
        catById: Map<com.hisabak.feature.category.domain.CategoryId, Category>,
        currency: Currency,
    ): List<BrandShare> {
        val grouped = transactions.groupBy { it.brandId }
        val total = grouped.values.sumOf { list -> list.sumOf { it.amount.amountMinor } }
        if (total == 0L) return emptyList()
        return grouped.mapNotNull { (brandId, list) ->
            val brand = brandById[brandId] ?: return@mapNotNull null
            val sum = list.sumOf { it.amount.amountMinor }
            BrandShare(
                id = brand.id,
                name = brand.name,
                color = brand.categoryId?.let { catById[it]?.color },
                amount = Money(sum, currency),
                pct = sum.toDouble() / total.toDouble(),
            )
        }.sortedByDescending { it.amount.amountMinor }
    }
}
