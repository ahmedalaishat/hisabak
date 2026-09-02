package com.hisabak.feature.transaction.presentation.list

import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.brand.domain.usecase.ObserveBrandsUseCase
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionListFilterCascadeTest : MainDispatcherTest() {

    private val dining = category(id = "c-dining", name = "Dining")
    private val rides = category(id = "c-rides", name = "Taxi & rides")

    private val nobu = brand(id = "b-nobu", name = "Nobu", categoryId = dining.id)
    private val yango = brand(id = "b-yango", name = "Yango", categoryId = rides.id)
    private val loose = brand(id = "b-loose", name = "Unfiled", categoryId = null)

    private fun vm(bus: TransactionListFilterBus = TransactionListFilterBus()): TransactionListViewModel {
        val brands = FakeBrandRepository(listOf(nobu, yango, loose))
        val categories = FakeCategoryRepository(listOf(dining, rides))
        val txs = FakeTransactionRepository(
            listOf(
                transaction(id = "t1", brandId = nobu.id.value),
                transaction(id = "t2", brandId = yango.id.value),
                transaction(id = "t3", brandId = loose.id.value),
            ),
        )
        return TransactionListViewModel(
            observeTransactions = ObserveTransactionsUseCase(txs),
            observeBrands = ObserveBrandsUseCase(brands),
            observeCategories = ObserveCategoriesUseCase(categories),
            clock = TestClock(),
            filterBus = bus,
        )
    }

    private fun TransactionListViewModel.brandNames() =
        state.value.brandOptions.map { it.name }

    @Test
    fun `with no category chosen every brand is offerable`() = runTest {
        val viewModel = vm()
        advanceUntilIdle()
        assertEquals(listOf("Nobu", "Unfiled", "Yango"), viewModel.brandNames())
    }

    @Test
    fun `choosing a category narrows the brand list to that category`() = runTest {
        val viewModel = vm()
        advanceUntilIdle()

        viewModel.onIntent(TransactionListIntent.CategoryFilterChanged(dining.id))
        advanceUntilIdle()

        assertEquals(listOf("Nobu"), viewModel.brandNames())
    }

    @Test
    fun `the uncategorized filter offers only brands without a category`() = runTest {
        val viewModel = vm()
        advanceUntilIdle()

        viewModel.onIntent(TransactionListIntent.CategoryFilterChanged(UncategorizedCategoryId))
        advanceUntilIdle()

        assertEquals(listOf("Unfiled"), viewModel.brandNames())
    }

    @Test
    fun `brand options carry their category color and icon for the sheet rows`() = runTest {
        val viewModel = vm()
        advanceUntilIdle()

        val nobuOption = viewModel.state.value.brandOptions.first { it.id == nobu.id }
        assertEquals(dining.color, nobuOption.categoryColor)
        assertEquals(dining.icon, nobuOption.categoryIcon)

        val looseOption = viewModel.state.value.brandOptions.first { it.id == loose.id }
        assertNull(looseOption.categoryColor)
        assertNull(looseOption.categoryIcon)
    }

    @Test
    fun `choosing a category drops a brand filter that would contradict it`() = runTest {
        val viewModel = vm()
        advanceUntilIdle()

        viewModel.onIntent(TransactionListIntent.BrandFilterChanged(yango.id))
        advanceUntilIdle()
        assertEquals(yango.id, viewModel.state.value.brandFilter)

        viewModel.onIntent(TransactionListIntent.CategoryFilterChanged(dining.id))
        advanceUntilIdle()

        assertNull(viewModel.state.value.brandFilter, "a stale brand would filter the list to nothing")
        assertTrue(viewModel.state.value.rows.isNotEmpty(), "Dining still has its own transaction")
    }

    @Test
    fun `widening back to all categories keeps the brand filter`() = runTest {
        val viewModel = vm()
        advanceUntilIdle()

        viewModel.onIntent(TransactionListIntent.BrandFilterChanged(yango.id))
        viewModel.onIntent(TransactionListIntent.CategoryFilterChanged(null))
        advanceUntilIdle()

        assertEquals(yango.id, viewModel.state.value.brandFilter)
    }

    @Test
    fun `the rows and the brand options agree on what a category contains`() = runTest {
        val viewModel = vm()
        advanceUntilIdle()

        viewModel.onIntent(TransactionListIntent.CategoryFilterChanged(rides.id))
        advanceUntilIdle()

        val offerable: List<BrandId> = viewModel.state.value.brandOptions.map { it.id }
        val shown: List<String?> = viewModel.state.value.rows.map { it.categoryName }
        assertEquals(listOf(yango.id), offerable)
        assertTrue(shown.isNotEmpty() && shown.all { it == rides.name })
    }
}

class TransactionListFilterRequestTest : MainDispatcherTest() {

    private val dining = category(id = "c-dining", name = "Dining")
    private val nobu = brand(id = "b-nobu", name = "Nobu", categoryId = dining.id)
    private val loose = brand(id = "b-loose", name = "Unfiled", categoryId = null)

    private fun vm(bus: TransactionListFilterBus): TransactionListViewModel {
        val brands = FakeBrandRepository(listOf(nobu, loose))
        val categories = FakeCategoryRepository(listOf(dining))
        val txs = FakeTransactionRepository(
            listOf(
                transaction(id = "t1", brandId = nobu.id.value),
                transaction(id = "t2", brandId = loose.id.value),
            ),
        )
        return TransactionListViewModel(
            observeTransactions = ObserveTransactionsUseCase(txs),
            observeBrands = ObserveBrandsUseCase(brands),
            observeCategories = ObserveCategoriesUseCase(categories),
            clock = TestClock(),
            filterBus = bus,
        )
    }

    @Test
    fun `a brand request filters to that brand and clears the rest`() = runTest {
        val bus = TransactionListFilterBus()
        val viewModel = vm(bus)
        advanceUntilIdle()
        viewModel.onIntent(TransactionListIntent.SearchChanged("stale"))
        advanceUntilIdle()

        bus.request(TransactionListFilterRequest.ByBrand(nobu.id))
        advanceUntilIdle()

        assertEquals(nobu.id, viewModel.state.value.brandFilter)
        assertNull(viewModel.state.value.categoryFilter)
        assertEquals("", viewModel.state.value.search, "a stale search would hide the rows we navigated to see")
        assertEquals(listOf("Nobu"), viewModel.state.value.rows.map { it.brandName })
    }

    @Test
    fun `a category request filters to that category`() = runTest {
        val bus = TransactionListFilterBus()
        val viewModel = vm(bus)
        advanceUntilIdle()

        bus.request(TransactionListFilterRequest.ByCategory(dining.id))
        advanceUntilIdle()

        assertEquals(dining.id, viewModel.state.value.categoryFilter)
        assertNull(viewModel.state.value.brandFilter)
        assertEquals(listOf("Nobu"), viewModel.state.value.rows.map { it.brandName })
    }

    @Test
    fun `the request is consumed so it does not reapply`() = runTest {
        val bus = TransactionListFilterBus()
        val viewModel = vm(bus)
        advanceUntilIdle()

        bus.request(TransactionListFilterRequest.ByBrand(nobu.id))
        advanceUntilIdle()
        assertNull(bus.pending.value)

        viewModel.onIntent(TransactionListIntent.BrandFilterChanged(null))
        advanceUntilIdle()
        assertNull(viewModel.state.value.brandFilter, "a lingering request would snap the filter back")
    }
}
