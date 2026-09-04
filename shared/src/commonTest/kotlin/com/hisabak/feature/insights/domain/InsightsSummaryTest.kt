package com.hisabak.feature.insights.domain

import com.hisabak.core.common.Currency
import com.hisabak.core.common.SummaryPeriod
import com.hisabak.feature.brand.domain.usecase.ObserveBrandsUseCase
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryLimit
import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.feature.category.domain.usecase.ObserveCategoriesUseCase
import com.hisabak.feature.category.domain.usecase.ObserveCategoryLimitsUseCase
import com.hisabak.feature.dashboard.domain.usecase.GetDashboardMetricsUseCase
import com.hisabak.feature.transaction.domain.usecase.ObserveTransactionsUseCase
import com.hisabak.testutil.FakeBrandRepository
import com.hisabak.testutil.FakeCategoryLimitRepository
import com.hisabak.testutil.FakeCategoryRepository
import com.hisabak.testutil.FakeTransactionRepository
import com.hisabak.testutil.TestClock
import com.hisabak.testutil.aed
import com.hisabak.testutil.brand
import com.hisabak.testutil.category
import com.hisabak.testutil.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth

/**
 * Builds the summary through the real metrics use case, so what the review sees is exactly what
 * the dashboard shows. TestClock is 2026-06-17: "this month" is June, the prior period is May.
 */
class InsightsSummaryTest {

    private val june = Instant.parse("2026-06-10T10:00:00Z")
    private val may = june - 30.days

    private val categories = FakeCategoryRepository(
        listOf(
            category(id = "inc", type = CategoryType.INCOME),
            category(id = "dining", name = "Dining", type = CategoryType.EXPENSES, color = "orange", icon = "food"),
            category(id = "fuel", name = "Fuel", type = CategoryType.EXPENSES),
        ),
    )
    private val brands = FakeBrandRepository(
        listOf(
            brand(id = "salary", categoryId = CategoryId("inc")),
            brand(id = "cafe", categoryId = CategoryId("dining")),
            brand(id = "pump", categoryId = CategoryId("fuel")),
            brand(id = "mystery", categoryId = null),
        ),
    )

    private fun useCase(transactions: FakeTransactionRepository, limits: FakeCategoryLimitRepository = FakeCategoryLimitRepository()) =
        GetDashboardMetricsUseCase(
            observeTransactions = ObserveTransactionsUseCase(transactions),
            observeCategories = ObserveCategoriesUseCase(categories),
            observeBrands = ObserveBrandsUseCase(brands),
            observeCategoryLimits = ObserveCategoryLimitsUseCase(limits),
            currency = Currency.AED,
            clock = TestClock(),
        )

    private suspend fun summaryFor(
        transactions: FakeTransactionRepository,
        limits: FakeCategoryLimitRepository = FakeCategoryLimitRepository(),
        period: SummaryPeriod = SummaryPeriod.CURRENT_MONTH,
    ): InsightsSummary = InsightsSummary.from(useCase(transactions, limits)(flowOf(period)).first(), period)

    @Test
    fun `totals and per-category and prior spend come from the snapshot`() = runTest {
        val summary = summaryFor(
            FakeTransactionRepository(
                listOf(
                    transaction(id = "s", amountMinor = 1_000_00, brandId = "salary", occurredAt = june),
                    transaction(id = "d1", amountMinor = 300_00, brandId = "cafe", occurredAt = june),
                    transaction(id = "f1", amountMinor = 100_00, brandId = "pump", occurredAt = june),
                    transaction(id = "d0", amountMinor = 200_00, brandId = "cafe", occurredAt = may),
                ),
            ),
        )
        assertEquals(1_000_00, summary.incomeMinor)
        assertEquals(400_00, summary.expenseMinor)

        val dining = summary.categories.single { it.id == CategoryId("dining") }
        assertEquals(300_00, dining.spentMinor)
        assertEquals(200_00, dining.priorMinor)
        assertEquals(0.75, dining.shareOfExpense, 0.001)
        assertEquals("Dining", dining.name)
        assertEquals("orange", dining.color)
        assertEquals("food", dining.icon)
    }

    @Test
    fun `only expense categories are listed and ordered by id`() = runTest {
        val summary = summaryFor(FakeTransactionRepository(emptyList()))
        assertEquals(listOf("dining", "fuel"), summary.categories.map { it.id.value })
    }

    @Test
    fun `the limit in force for the period is mapped onto its category`() = runTest {
        val limits = FakeCategoryLimitRepository(
            listOf(CategoryLimit(CategoryId("dining"), aed(500_00), YearMonth(2026, 1))),
        )
        val summary = summaryFor(
            FakeTransactionRepository(listOf(transaction(id = "d1", amountMinor = 50_00, brandId = "cafe", occurredAt = june))),
            limits,
        )
        assertEquals(500_00, summary.categories.single { it.id == CategoryId("dining") }.limitMinor)
        assertNull(summary.categories.single { it.id == CategoryId("fuel") }.limitMinor)
    }

    @Test
    fun `a yearly period carries the months' caps summed - what the Categories tab shows`() = runTest {
        val limits = FakeCategoryLimitRepository(
            listOf(
                CategoryLimit(CategoryId("dining"), aed(500_00), YearMonth(2026, 1)),
                CategoryLimit(CategoryId("dining"), aed(800_00), YearMonth(2026, 4)),
            ),
        )
        val summary = summaryFor(
            FakeTransactionRepository(listOf(transaction(id = "d1", amountMinor = 50_00, brandId = "cafe", occurredAt = june))),
            limits,
            period = SummaryPeriod.CURRENT_YEAR,
        )
        // The year's buckets run to today (June): Jan–Mar at 500, Apr–Jun at 800 — the budget so
        // far, which is what the spend so far is measured against.
        assertEquals(3 * 500_00L + 3 * 800_00L, summary.categories.single { it.id == CategoryId("dining") }.limitMinor)
    }

    @Test
    fun `uncategorized spend is carried as a total and a count`() = runTest {
        val summary = summaryFor(
            FakeTransactionRepository(
                listOf(
                    transaction(id = "m1", amountMinor = 40_00, brandId = "mystery", occurredAt = june),
                    transaction(id = "m2", amountMinor = 60_00, brandId = "mystery", occurredAt = june),
                ),
            ),
        )
        assertEquals(100_00, summary.uncategorizedMinor)
        assertEquals(2, summary.uncategorizedCount)
    }

    /** The snapshot exposes the prior totals only as a trend percentage; the summary recovers them. */
    @Test
    fun `prior income and expense are recovered from the trend percentages`() = runTest {
        val summary = summaryFor(
            FakeTransactionRepository(
                listOf(
                    transaction(id = "s1", amountMinor = 1_200_00, brandId = "salary", occurredAt = june),
                    transaction(id = "s0", amountMinor = 1_000_00, brandId = "salary", occurredAt = may),
                    transaction(id = "d1", amountMinor = 300_00, brandId = "cafe", occurredAt = june),
                    transaction(id = "d0", amountMinor = 400_00, brandId = "cafe", occurredAt = may),
                ),
            ),
        )
        assertEquals(1_000_00, summary.priorIncomeMinor)
        assertEquals(400_00, summary.priorExpenseMinor)
    }

    @Test
    fun `all time has no prior period`() = runTest {
        val summary = summaryFor(
            FakeTransactionRepository(listOf(transaction(id = "d1", amountMinor = 300_00, brandId = "cafe", occurredAt = june))),
            period = SummaryPeriod.ALL,
        )
        assertNull(summary.priorExpenseMinor)
        assertNull(summary.categories.single { it.id == CategoryId("dining") }.priorMinor)
    }
}
