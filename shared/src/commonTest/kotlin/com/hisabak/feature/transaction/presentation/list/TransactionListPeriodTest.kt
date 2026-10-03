package com.hisabak.feature.transaction.presentation.list

import com.hisabak.core.common.SummaryPeriod
import com.hisabak.core.presentation.PeriodSelection
import com.hisabak.feature.brand.domain.usecase.ObserveBrandsUseCase
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.feature.category.domain.usecase.ObserveCategoriesUseCase
import com.hisabak.feature.transaction.domain.usecase.ObserveTransactionsUseCase
import com.hisabak.testutil.FakeBrandRepository
import com.hisabak.testutil.FakeCategoryRepository
import com.hisabak.testutil.FakeTransactionRepository
import com.hisabak.testutil.MainDispatcherTest
import com.hisabak.testutil.TestClock
import com.hisabak.testutil.brand
import com.hisabak.testutil.category
import com.hisabak.testutil.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/** The list and its totals follow the one shared period; the old rolling-days filter is gone. */
class TransactionListPeriodTest : MainDispatcherTest() {

    private val selection = PeriodSelection(TestClock()) // today: 2026-06-17

    private fun vm() = TransactionListViewModel(
        observeTransactions = ObserveTransactionsUseCase(
            FakeTransactionRepository(
                listOf(
                    transaction(id = "jun", amountMinor = 300_00, brandId = "cafe", occurredAt = Instant.parse("2026-06-02T10:00:00Z")),
                    transaction(id = "may", amountMinor = 200_00, brandId = "cafe", occurredAt = Instant.parse("2026-05-20T10:00:00Z")),
                    transaction(id = "old", amountMinor = 100_00, brandId = "cafe", occurredAt = Instant.parse("2024-03-01T10:00:00Z")),
                ),
            ),
        ),
        observeBrands = ObserveBrandsUseCase(FakeBrandRepository(listOf(brand(id = "cafe", categoryId = CategoryId("dining"))))),
        observeCategories = ObserveCategoriesUseCase(FakeCategoryRepository(listOf(category(id = "dining", type = CategoryType.EXPENSES)))),
        clock = TestClock(),
        filterBus = TransactionListFilterBus(),
        periodSelection = selection,
    )

    private fun TransactionListViewModel.ids() = state.value.rows.map { it.id.value }

    @Test
    fun `the list opens on this month for totals and rows alike`() = runTest {
        val viewModel = vm()
        advanceUntilIdle()

        assertEquals(listOf("jun"), viewModel.ids())
        assertEquals(300_00, viewModel.state.value.summaryExpenses)
        assertEquals(1, viewModel.state.value.totalCount)
    }

    @Test
    fun `changing the period here changes it everywhere`() = runTest {
        val viewModel = vm()
        advanceUntilIdle()

        viewModel.onIntent(TransactionListIntent.PeriodChanged(SummaryPeriod.Month(YearMonth(2026, 5))))
        advanceUntilIdle()

        assertEquals(SummaryPeriod.Month(YearMonth(2026, 5)), selection.period.value)
        assertEquals(listOf("may"), viewModel.ids())
    }

    @Test
    fun `a period picked on another tab re-scopes the list`() = runTest {
        val viewModel = vm()
        advanceUntilIdle()

        selection.select(SummaryPeriod.All)
        advanceUntilIdle()

        assertEquals(listOf("jun", "may", "old"), viewModel.ids())
        assertEquals(600_00, viewModel.state.value.summaryExpenses)
    }

    @Test
    fun `the bar learns today and the first activity`() = runTest {
        val viewModel = vm()
        advanceUntilIdle()

        assertEquals(LocalDate(2026, 6, 17), viewModel.state.value.today)
        assertEquals(LocalDate(2024, 3, 1), viewModel.state.value.earliest)
    }
}
