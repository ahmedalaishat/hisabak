package com.hisabak.feature.brand.presentation.list

import com.hisabak.core.common.DomainError
import com.hisabak.core.common.DomainResult
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.brand.domain.usecase.DeleteBrandUseCase
import com.hisabak.feature.brand.domain.usecase.ObserveBrandsUseCase
import com.hisabak.feature.category.domain.usecase.ObserveCategoriesUseCase
import com.hisabak.feature.transaction.domain.usecase.ObserveTransactionsUseCase
import com.hisabak.feature.transaction.domain.usecase.ReassignBrandTransactionsUseCase
import com.hisabak.testutil.FakeAnalytics
import com.hisabak.testutil.FakeBrandRepository
import com.hisabak.testutil.FakeCategoryRepository
import com.hisabak.testutil.FakeTransactionRepository
import com.hisabak.testutil.MainDispatcherTest
import com.hisabak.testutil.brand
import com.hisabak.testutil.transaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrandListViewModelTest : MainDispatcherTest() {

    private val brandRepo = FakeBrandRepository(
        listOf(brand(id = "b1", name = "Lulu"), brand(id = "b2", name = "Carrefour")),
    )
    private val catRepo = FakeCategoryRepository()
    private val txRepo = FakeTransactionRepository()
    private val analytics = FakeAnalytics()

    private fun viewModel() = BrandListViewModel(
        observeBrands = ObserveBrandsUseCase(brandRepo),
        observeCategories = ObserveCategoriesUseCase(catRepo),
        observeTransactions = ObserveTransactionsUseCase(txRepo),
        deleteBrand = DeleteBrandUseCase(brandRepo),
        reassignBrandTransactions = ReassignBrandTransactionsUseCase(txRepo),
        analytics = analytics,
    )

    @Test
    fun `a successful delete emits no error effect`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(BrandListIntent.Delete(BrandId("b1")))
        advanceUntilIdle()

        assertTrue(brandRepo.current.none { it.id == BrandId("b1") })
        assertNull(vm.effect.value)
    }

    @Test
    fun `merging a brand reassigns transactions and logs the merge`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(BrandListIntent.MergeAndDelete(sourceId = BrandId("b1"), targetId = BrandId("b2")))
        advanceUntilIdle()

        assertTrue(brandRepo.current.none { it.id == BrandId("b1") }) // source removed
        assertEquals(listOf("brand_merged"), analytics.names())
    }

    @Test
    fun `a failed delete surfaces a message instead of failing silently`() = runTest {
        brandRepo.deleteResult = DomainResult.Failure(DomainError.Unexpected(IllegalStateException("FK")))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(BrandListIntent.Delete(BrandId("b1")))
        advanceUntilIdle()

        assertTrue(brandRepo.current.isNotEmpty()) // brand kept
        assertTrue(vm.effect.value is BrandListEffect.Message)
    }

    @Test
    fun `brand rows carry the summed transaction total`() = runTest {
        val txRepo = FakeTransactionRepository(
            listOf(
                transaction(id = "t1", brandId = "b1", amountMinor = 1_000L),
                transaction(id = "t2", brandId = "b1", amountMinor = 500L),
                transaction(id = "t3", brandId = "b2", amountMinor = 250L),
            ),
        )
        val vm = BrandListViewModel(
            observeBrands = ObserveBrandsUseCase(brandRepo),
            observeCategories = ObserveCategoriesUseCase(catRepo),
            observeTransactions = ObserveTransactionsUseCase(txRepo),
            deleteBrand = DeleteBrandUseCase(brandRepo),
            reassignBrandTransactions = ReassignBrandTransactionsUseCase(txRepo),
            analytics = analytics,
        )
        advanceUntilIdle()

        val rows = vm.state.value.rows.associateBy { it.id }
        assertEquals(1_500L, rows.getValue(BrandId("b1")).totalMinor)
        assertEquals(250L, rows.getValue(BrandId("b2")).totalMinor)
    }

    @Test
    fun `withdrawals net against the brand total down to zero`() = runTest {
        // Lend 2k, get 1k back → 1k outstanding; b2 is fully repaid → 0, with its count kept
        // so the row still shows the total instead of hiding it.
        val txRepo = FakeTransactionRepository(
            listOf(
                transaction(id = "t1", brandId = "b1", amountMinor = 2_000_00L),
                transaction(id = "t2", brandId = "b1", amountMinor = -1_000_00L),
                transaction(id = "t3", brandId = "b2", amountMinor = 500_00L),
                transaction(id = "t4", brandId = "b2", amountMinor = -500_00L),
            ),
        )
        val vm = BrandListViewModel(
            observeBrands = ObserveBrandsUseCase(brandRepo),
            observeCategories = ObserveCategoriesUseCase(catRepo),
            observeTransactions = ObserveTransactionsUseCase(txRepo),
            deleteBrand = DeleteBrandUseCase(brandRepo),
            reassignBrandTransactions = ReassignBrandTransactionsUseCase(txRepo),
            analytics = analytics,
        )
        advanceUntilIdle()

        val rows = vm.state.value.rows.associateBy { it.id }
        assertEquals(1_000_00L, rows.getValue(BrandId("b1")).totalMinor)
        assertEquals(0L, rows.getValue(BrandId("b2")).totalMinor)
        assertEquals(2, rows.getValue(BrandId("b2")).transactionCount)
    }
}
