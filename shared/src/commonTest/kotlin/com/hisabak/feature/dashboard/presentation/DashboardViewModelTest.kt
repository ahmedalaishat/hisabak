package com.hisabak.feature.dashboard.presentation

import com.hisabak.core.common.Currency
import com.hisabak.core.common.SummaryPeriod
import com.hisabak.feature.brand.domain.usecase.ObserveBrandsUseCase
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.feature.category.domain.usecase.ObserveCategoriesUseCase
import com.hisabak.feature.category.domain.usecase.ObserveCategoryLimitsUseCase
import com.hisabak.feature.dashboard.domain.usecase.GetDashboardMetricsUseCase
import com.hisabak.feature.insights.domain.InsightType
import com.hisabak.feature.transaction.domain.usecase.ObserveTransactionsUseCase
import com.hisabak.testutil.FakeAnalytics
import com.hisabak.testutil.FakeBrandRepository
import com.hisabak.testutil.FakeCategoryLimitRepository
import com.hisabak.testutil.FakeCategoryRepository
import com.hisabak.testutil.FakeTransactionRepository
import com.hisabak.testutil.MainDispatcherTest
import com.hisabak.testutil.TestClock
import com.hisabak.testutil.brand
import com.hisabak.testutil.category
import com.hisabak.testutil.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class DashboardViewModelTest : MainDispatcherTest() {

    private val june = Instant.parse("2026-06-10T10:00:00Z")
    private val may = june - 30.days

    private val metrics = GetDashboardMetricsUseCase(
        observeTransactions = ObserveTransactionsUseCase(
            FakeTransactionRepository(
                listOf(
                    transaction(id = "d1", amountMinor = 500_00, brandId = "cafe", occurredAt = june),
                    transaction(id = "d0", amountMinor = 200_00, brandId = "cafe", occurredAt = may),
                ),
            ),
        ),
        observeCategories = ObserveCategoriesUseCase(
            FakeCategoryRepository(listOf(category(id = "dining", type = CategoryType.EXPENSES))),
        ),
        observeBrands = ObserveBrandsUseCase(
            FakeBrandRepository(listOf(brand(id = "cafe", categoryId = CategoryId("dining")))),
        ),
        observeCategoryLimits = ObserveCategoryLimitsUseCase(FakeCategoryLimitRepository()),
        currency = Currency.AED,
        clock = TestClock(),
    )

    /** The card can never lag the numbers: both arrive in the same state emission. */
    @Test
    fun `the review is derived alongside the snapshot`() = runTest {
        val vm = DashboardViewModel(getMetrics = metrics, analytics = FakeAnalytics())
        advanceUntilIdle()

        val state = vm.state.value
        assertNotNull(state.snapshot)
        assertTrue(state.review.any { it.type == InsightType.SpendUp }) // 200 → 500 vs May
        assertTrue(state.review.any { it.type == InsightType.LargestCategory })
    }

    @Test
    fun `changing the period re-derives the review for it`() = runTest {
        val vm = DashboardViewModel(getMetrics = metrics, analytics = FakeAnalytics())
        advanceUntilIdle()

        vm.onIntent(DashboardIntent.PeriodChanged(SummaryPeriod.ALL))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SummaryPeriod.ALL, state.period)
        // No prior period for ALL, so the change insight is gone but the review still stands.
        assertTrue(state.review.none { it.type == InsightType.SpendUp })
        assertTrue(state.review.any { it.type == InsightType.LargestCategory })
    }
}
