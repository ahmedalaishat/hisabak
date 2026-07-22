package com.hisabak.feature.dashboard.domain.usecase

import com.hisabak.core.common.Currency
import com.hisabak.core.common.SummaryPeriod
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
import org.junit.Assert.assertEquals
import org.junit.Test

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

        val snapshot = useCase(flowOf(SummaryPeriod.ALL)).first()

        assertEquals(aed(1_000_00), snapshot.income)
        assertEquals(aed(300_00), snapshot.expense)
        assertEquals(aed(700_00), snapshot.netWorth) // income - expenses
        assertEquals(aed(200_00), snapshot.totalSavings)
        assertEquals(aed(100_00), snapshot.totalInvestment)
        assertEquals(aed(400_00), snapshot.totalCash) // netWorth - savings - investment
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

        val snapshot = useCase(flowOf(SummaryPeriod.ALL)).first()

        assertEquals(1, snapshot.uncategorizedCount)
        assertEquals(aed(50_00), snapshot.uncategorizedTotal)
    }
}
