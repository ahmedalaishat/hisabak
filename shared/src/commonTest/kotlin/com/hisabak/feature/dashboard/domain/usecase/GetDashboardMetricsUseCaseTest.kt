package com.hisabak.feature.dashboard.domain.usecase

import com.hisabak.core.common.Currency
import com.hisabak.core.common.Granularity
import com.hisabak.core.common.SummaryPeriod
import com.hisabak.feature.dashboard.domain.DayPoint
import com.hisabak.testutil.categoryLimit
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import com.hisabak.feature.brand.domain.usecase.ObserveBrandsUseCase
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.feature.category.domain.usecase.ObserveCategoriesUseCase
import com.hisabak.feature.category.domain.usecase.ObserveCategoryLimitsUseCase
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.Test

class GetDashboardMetricsUseCaseTest {

    @Test
    fun `net worth and cash derive from typed transaction sums`() = runTest {
        val categories = FakeCategoryRepository(
            listOf(
                category(id = "inc", type = CategoryType.INCOME),
                category(id = "exp", type = CategoryType.EXPENSES),
                category(id = "sav", type = CategoryType.SAVINGS),
                category(id = "inv", type = CategoryType.INVESTMENT),
            ),
        )
        val brands = FakeBrandRepository(
            listOf(
                brand(id = "bi", categoryId = CategoryId("inc")),
                brand(id = "be", categoryId = CategoryId("exp")),
                brand(id = "bs", categoryId = CategoryId("sav")),
                brand(id = "bv", categoryId = CategoryId("inv")),
            ),
        )
        val transactions = FakeTransactionRepository(
            listOf(
                transaction(id = "t1", amountMinor = 1_000_00, brandId = "bi"),
                transaction(id = "t2", amountMinor = 300_00, brandId = "be"),
                transaction(id = "t3", amountMinor = 200_00, brandId = "bs"),
                transaction(id = "t4", amountMinor = 100_00, brandId = "bv"),
            ),
        )
        val useCase = GetDashboardMetricsUseCase(
            observeTransactions = ObserveTransactionsUseCase(transactions),
            observeCategories = ObserveCategoriesUseCase(categories),
            observeBrands = ObserveBrandsUseCase(brands),
            observeCategoryLimits = ObserveCategoryLimitsUseCase(FakeCategoryLimitRepository()),
            currency = Currency.AED,
            clock = TestClock(),
        )

        val snapshot = useCase(flowOf(SummaryPeriod.All)).first()

        assertEquals(aed(1_000_00), snapshot.income)
        assertEquals(aed(300_00), snapshot.expense)
        assertEquals(aed(700_00), snapshot.netWorth) // income - expenses
        assertEquals(aed(200_00), snapshot.totalSavings)
        assertEquals(aed(100_00), snapshot.totalInvestment)
        assertEquals(aed(400_00), snapshot.totalCash) // netWorth - savings - investment
    }

    @Test
    fun `a savings withdrawal nets the savings total and releases cash`() = runTest {
        val categories = FakeCategoryRepository(
            listOf(
                category(id = "inc", type = CategoryType.INCOME),
                category(id = "sav", type = CategoryType.SAVINGS),
            ),
        )
        val brands = FakeBrandRepository(
            listOf(
                brand(id = "bi", categoryId = CategoryId("inc")),
                brand(id = "bs", categoryId = CategoryId("sav")),
            ),
        )
        val transactions = FakeTransactionRepository(
            listOf(
                transaction(id = "t1", amountMinor = 5_000_00, brandId = "bi"),
                transaction(id = "t2", amountMinor = 2_000_00, brandId = "bs"), // lend / deposit
                transaction(id = "t3", amountMinor = -1_000_00, brandId = "bs"), // repayment / withdrawal
            ),
        )
        val useCase = GetDashboardMetricsUseCase(
            observeTransactions = ObserveTransactionsUseCase(transactions),
            observeCategories = ObserveCategoriesUseCase(categories),
            observeBrands = ObserveBrandsUseCase(brands),
            observeCategoryLimits = ObserveCategoryLimitsUseCase(FakeCategoryLimitRepository()),
            currency = Currency.AED,
            clock = TestClock(),
        )

        val snapshot = useCase(flowOf(SummaryPeriod.All)).first()

        assertEquals(aed(1_000_00), snapshot.totalSavings) // 2k out, 1k back
        assertEquals(aed(4_000_00), snapshot.totalCash) // netWorth 5k - net savings 1k
    }

    @Test
    fun `transactions whose brand has no category surface as uncategorized`() = runTest {
        val categories = FakeCategoryRepository(listOf(category(id = "exp", type = CategoryType.EXPENSES)))
        val brands = FakeBrandRepository(listOf(brand(id = "borphan", categoryId = null)))
        val transactions = FakeTransactionRepository(listOf(transaction(amountMinor = 50_00, brandId = "borphan")))
        val useCase = GetDashboardMetricsUseCase(
            observeTransactions = ObserveTransactionsUseCase(transactions),
            observeCategories = ObserveCategoriesUseCase(categories),
            observeBrands = ObserveBrandsUseCase(brands),
            observeCategoryLimits = ObserveCategoryLimitsUseCase(FakeCategoryLimitRepository()),
            currency = Currency.AED,
            clock = TestClock(),
        )

        val snapshot = useCase(flowOf(SummaryPeriod.All)).first()

        assertEquals(1, snapshot.uncategorizedCount)
        assertEquals(aed(50_00), snapshot.uncategorizedTotal)
    }

    // TestClock's today is 2026-06-17.
    private fun useCase(
        txs: List<com.hisabak.feature.transaction.domain.Transaction>,
        limits: List<com.hisabak.feature.category.domain.CategoryLimit> = emptyList(),
    ) = GetDashboardMetricsUseCase(
        observeTransactions = ObserveTransactionsUseCase(FakeTransactionRepository(txs)),
        observeCategories = ObserveCategoriesUseCase(FakeCategoryRepository(listOf(category(id = "exp", type = CategoryType.EXPENSES)))),
        observeBrands = ObserveBrandsUseCase(FakeBrandRepository(listOf(brand(id = "be", categoryId = CategoryId("exp"))))),
        observeCategoryLimits = ObserveCategoryLimitsUseCase(FakeCategoryLimitRepository(limits)),
        currency = Currency.AED,
        clock = TestClock(),
    )

    private fun spendOn(id: String, date: String, minor: Long) =
        transaction(id = id, amountMinor = minor, brandId = "be", occurredAt = Instant.parse("${date}T10:00:00Z"))

    @Test
    fun `a past month is daily across all its days`() = runTest {
        val snapshot = useCase(listOf(spendOn("t1", "2026-03-10", 40_00)))(
            flowOf(SummaryPeriod.Month(YearMonth(2026, 3))),
        ).first()

        assertEquals(Granularity.DAY, snapshot.granularity)
        assertEquals(31, snapshot.expenseDaily.size)
        assertEquals(40_00, snapshot.expenseDaily.single { it.day == LocalDate(2026, 3, 10) }.amountMinor)
        assertEquals(aed(40_00), snapshot.expense)
    }

    @Test
    fun `ninety days is weekly and bucketed from its first day`() = runTest {
        val period = SummaryPeriod.lastDays(LocalDate(2026, 6, 17), 90)
        val snapshot = useCase(
            listOf(spendOn("t1", "2026-03-20", 10_00), spendOn("t2", "2026-03-26", 5_00), spendOn("t3", "2026-03-27", 7_00)),
        )(flowOf(period)).first()

        assertEquals(Granularity.WEEK, snapshot.granularity)
        assertEquals(13, snapshot.expenseDaily.size)
        // The window starts 2026-03-20, so 20–26 Mar is one week and the 27th starts the next.
        assertEquals(DayPoint(LocalDate(2026, 3, 20), 15_00), snapshot.expenseDaily[0])
        assertEquals(DayPoint(LocalDate(2026, 3, 27), 7_00), snapshot.expenseDaily[1])
    }

    @Test
    fun `all time over many years is yearly from the first transaction`() = runTest {
        val snapshot = useCase(
            listOf(spendOn("t1", "2019-04-02", 10_00), spendOn("t2", "2026-01-05", 20_00)),
        )(flowOf(SummaryPeriod.All)).first()

        assertEquals(Granularity.YEAR, snapshot.granularity)
        assertEquals((2019..2026).map { LocalDate(it, 1, 1) }, snapshot.expenseDaily.map { it.day })
        assertEquals(LocalDate(2019, 4, 2), snapshot.earliestActivity)
    }

    @Test
    fun `a month's limit is the month's cap`() = runTest {
        val snapshot = useCase(
            listOf(spendOn("t1", "2026-06-02", 10_00)),
            limits = listOf(categoryLimit("exp", 3_000_00, effectiveFrom = YearMonth(2026, 1))),
        )(flowOf(SummaryPeriod.thisMonth(LocalDate(2026, 6, 17)))).first()

        assertEquals(3_000_00, snapshot.periodLimitByCategory[CategoryId("exp")])
    }

    @Test
    fun `this year's limit counts the months so far`() = runTest {
        val snapshot = useCase(
            listOf(spendOn("t1", "2026-02-02", 10_00)),
            limits = listOf(categoryLimit("exp", 1_000_00, effectiveFrom = YearMonth(2026, 1))),
        )(flowOf(SummaryPeriod.thisYear(LocalDate(2026, 6, 17)))).first()

        assertEquals(6_000_00, snapshot.periodLimitByCategory[CategoryId("exp")]) // January to June
    }
}
