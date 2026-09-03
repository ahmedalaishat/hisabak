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
import com.hisabak.testutil.category
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlinx.datetime.YearMonth
import com.hisabak.feature.category.domain.usecase.DeleteCategoryUseCase
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryEditViewModelTest : MainDispatcherTest() {

    private val clock = TestClock() // 2026-06
    private val catRepo = FakeCategoryRepository()
    private val limitRepo = FakeCategoryLimitRepository()
    private val analytics = FakeAnalytics()

    private fun viewModel(
        categoryId: CategoryId? = null,
        prefill: CategoryEditPrefill? = null,
        proposedLimitMinor: Long? = null,
    ) = CategoryEditViewModel(
        categoryId = categoryId,
        prefill = prefill,
        proposedLimitMinor = proposedLimitMinor,
        categoryRepository = catRepo,
        createCategory = CreateCategoryUseCase(catRepo),
        updateCategory = UpdateCategoryUseCase(catRepo),
        deleteCategory = DeleteCategoryUseCase(catRepo),
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
    fun `an AI prefill seeds a new category's fields`() = runTest {
        val vm = viewModel(
            prefill = CategoryEditPrefill("Pharmacy", CategoryType.EXPENSES, "teal", "heart"),
        )
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("Pharmacy", s.nameInput)
        assertEquals(CategoryType.EXPENSES, s.type)
        assertEquals("teal", s.color)
        assertEquals("heart", s.icon)
        assertEquals(true, s.isNew)
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
        // Saved carries the created id so the "+ New category" detour can select it.
        assertEquals(CategoryEditEffect.Saved(category.id), vm.effect.value)
        assertEquals(aed(500_00), limitRepo.current.effectiveFor(category.id, YearMonth(2026, 6)))

        val event = analytics.logged.single() as AnalyticsEvent.CategoryCreated
        assertEquals("expenses", event.params["type"])
        assertEquals(true, event.params["has_limit"])
        // PII guard: the category name never reaches analytics.
        assertTrue(event.params.values.none { it == "Groceries" })
    }

    @Test
    fun `updating an existing category emits Saved with its id`() = runTest {
        catRepo.emit(listOf(category(id = "c1", name = "Food")))
        val vm = viewModel(CategoryId("c1"))
        advanceUntilIdle()

        vm.onIntent(CategoryEditIntent.NameChanged("Dining"))
        vm.onIntent(CategoryEditIntent.Save)
        advanceUntilIdle()

        assertEquals(CategoryEditEffect.Saved(CategoryId("c1")), vm.effect.value)
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

    @Test
    fun `deleting an existing category reports it so the caller can leave`() = runTest {
        catRepo.upsert(category(id = "c1", name = "Dining"))
        val vm = viewModel(CategoryId("c1"))
        advanceUntilIdle()

        vm.onIntent(CategoryEditIntent.Delete)
        advanceUntilIdle()

        assertEquals(CategoryEditEffect.Deleted, vm.effect.value)
        assertTrue(catRepo.observeAll().first().none { it.id.value == "c1" })
    }

    @Test
    fun `a new category has nothing to delete`() = runTest {
        val vm = viewModel(null)
        advanceUntilIdle()

        vm.onIntent(CategoryEditIntent.Delete)
        advanceUntilIdle()

        assertEquals(null, vm.effect.value, "delete is not offered for an unsaved category")
    }

    @Test
    fun `a proposed limit fills the field but is not saved until Save`() = runTest {
        catRepo.upsert(category(id = "dining", type = CategoryType.EXPENSES))
        val vm = viewModel(categoryId = CategoryId("dining"), proposedLimitMinor = 1_600_00)
        advanceUntilIdle()

        assertEquals("1600", vm.state.value.limitInput)
        assertTrue(limitRepo.observeAll().first().isEmpty())
    }
}
