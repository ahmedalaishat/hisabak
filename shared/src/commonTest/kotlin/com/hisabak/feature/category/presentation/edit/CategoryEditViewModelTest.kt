package com.hisabak.feature.category.presentation.edit

import com.hisabak.core.common.Currency
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.feature.category.domain.effectiveFor
import com.hisabak.feature.category.domain.usecase.CreateCategoryUseCase
import com.hisabak.feature.category.domain.usecase.ObserveCategoryLimitsUseCase
import com.hisabak.feature.category.domain.usecase.SetCategoryLimitUseCase
import com.hisabak.feature.category.domain.usecase.UpdateCategoryUseCase
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.testutil.FakeAnalytics
import com.hisabak.testutil.FakeCategoryLimitRepository
import com.hisabak.testutil.FakeCategoryRepository
import com.hisabak.testutil.MainDispatcherTest
import com.hisabak.testutil.TestClock
import com.hisabak.testutil.aed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlinx.datetime.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryEditViewModelTest : MainDispatcherTest() {

    private val clock = TestClock() // 2026-06
    private val catRepo = FakeCategoryRepository()
    private val limitRepo = FakeCategoryLimitRepository()
    private val analytics = FakeAnalytics()

    private fun viewModel(categoryId: CategoryId? = null) = CategoryEditViewModel(
        categoryId = categoryId,
        categoryRepository = catRepo,
        createCategory = CreateCategoryUseCase(catRepo),
        updateCategory = UpdateCategoryUseCase(catRepo),
        observeCategoryLimits = ObserveCategoryLimitsUseCase(limitRepo),
        setCategoryLimit = SetCategoryLimitUseCase(limitRepo, clock),
        currency = Currency.AED,
        clock = clock,
        analytics = analytics,
    )

    @Test
    fun `editing an existing category is not flagged as new`() = runTest {
        // Guards the BaseViewModel init-order fix: initialState() must see the constructor's
        // categoryId, so an edit is titled "Edit category", not "New category".
        val vm = viewModel(CategoryId("c1"))
        advanceUntilIdle()
        assertEquals(false, vm.state.value.isNew)
    }

    @Test
    fun `a blank name is rejected`() = runTest {
        val vm = viewModel()
        vm.onIntent(CategoryEditIntent.Save)
        advanceUntilIdle()

        assertTrue(vm.state.value.nameError != null)
        assertTrue(catRepo.current.isEmpty())
    }

    @Test
    fun `creating an expense category persists its monthly limit`() = runTest {
        val vm = viewModel()
        vm.onIntent(CategoryEditIntent.NameChanged("Groceries"))
        vm.onIntent(CategoryEditIntent.TypeChanged(CategoryType.EXPENSES))
        vm.onIntent(CategoryEditIntent.LimitChanged("500"))

        vm.onIntent(CategoryEditIntent.Save)
        advanceUntilIdle()

        val category = catRepo.current.single()
        assertEquals("Groceries", category.name)
        assertEquals(CategoryEditEffect.Saved, vm.effect.value)
        assertEquals(aed(500_00), limitRepo.current.effectiveFor(category.id, YearMonth(2026, 6)))

        val event = analytics.logged.single() as AnalyticsEvent.CategoryCreated
        assertEquals("expenses", event.params["type"])
        assertEquals(true, event.params["has_limit"])
        // PII guard: the category name never reaches analytics.
        assertTrue(event.params.values.none { it == "Groceries" })
    }

    @Test
    fun `non-expense categories do not record a limit`() = runTest {
        val vm = viewModel()
        vm.onIntent(CategoryEditIntent.NameChanged("Salary"))
        vm.onIntent(CategoryEditIntent.TypeChanged(CategoryType.INCOME))
        vm.onIntent(CategoryEditIntent.LimitChanged("500")) // ignored: limits are expense-only

        vm.onIntent(CategoryEditIntent.Save)
        advanceUntilIdle()

        assertEquals(1, catRepo.current.size)
        assertTrue(limitRepo.current.isEmpty())
    }

    @Test
    fun `an invalid limit blocks saving`() = runTest {
        val vm = viewModel()
        vm.onIntent(CategoryEditIntent.NameChanged("Groceries"))
        vm.onIntent(CategoryEditIntent.LimitChanged("0"))

        vm.onIntent(CategoryEditIntent.Save)
        advanceUntilIdle()

        assertTrue(vm.state.value.limitError != null)
        assertTrue(catRepo.current.isEmpty())
    }
}
