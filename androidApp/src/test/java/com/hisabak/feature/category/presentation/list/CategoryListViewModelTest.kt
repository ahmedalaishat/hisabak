package com.hisabak.feature.category.presentation.list

import com.hisabak.feature.brand.domain.usecase.ObserveBrandsUseCase
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.usecase.DeleteCategoryUseCase
import com.hisabak.feature.category.domain.usecase.ObserveCategoriesUseCase
import com.hisabak.feature.transaction.domain.usecase.ObserveTransactionsUseCase
import com.hisabak.testutil.FakeBrandRepository
import com.hisabak.testutil.FakeCategoryRepository
import com.hisabak.testutil.FakeTransactionRepository
import com.hisabak.testutil.MainDispatcherRule
import com.hisabak.testutil.brand
import com.hisabak.testutil.category
import com.hisabak.testutil.transaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `category rows sum the totals of their brands' transactions`() = runTest {
        val catRepo = FakeCategoryRepository(
            listOf(category(id = "c1", name = "Groceries"), category(id = "c2", name = "Salary")),
        )
        val brandRepo = FakeBrandRepository(
            listOf(
                brand(id = "b1", categoryId = CategoryId("c1")),
                brand(id = "b2", categoryId = CategoryId("c1")),
                brand(id = "b3", categoryId = CategoryId("c2")),
            ),
        )
        val txRepo = FakeTransactionRepository(
            listOf(
                transaction(id = "t1", brandId = "b1", amountMinor = 1_000L),
                transaction(id = "t2", brandId = "b2", amountMinor = 500L),
                transaction(id = "t3", brandId = "b3", amountMinor = 8_000L),
            ),
        )
        val vm = CategoryListViewModel(
            observeCategories = ObserveCategoriesUseCase(catRepo),
            observeBrands = ObserveBrandsUseCase(brandRepo),
            observeTransactions = ObserveTransactionsUseCase(txRepo),
            deleteCategory = DeleteCategoryUseCase(catRepo),
        )
        advanceUntilIdle()

        val rows = vm.state.value.rows.associateBy { it.id }
        assertEquals(1_500L, rows.getValue(CategoryId("c1")).totalMinor)
        assertEquals(8_000L, rows.getValue(CategoryId("c2")).totalMinor)
    }
}
