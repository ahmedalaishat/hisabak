package com.hisabak.feature.brand.presentation.edit

import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.brand.domain.ai.AiRawCategorySuggestion
import com.hisabak.feature.brand.domain.ai.CategorySuggestion
import com.hisabak.feature.brand.domain.ai.SuggestBrandCategoryUseCase
import com.hisabak.feature.brand.domain.usecase.CreateBrandUseCase
import com.hisabak.feature.brand.domain.usecase.UpdateBrandUseCase
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.usecase.ObserveCategoriesUseCase
import com.hisabak.feature.category.presentation.CategoryCreatedBus
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.testutil.FakeAiCategorySuggester
import com.hisabak.testutil.FakeAnalytics
import com.hisabak.testutil.FakeBrandRepository
import com.hisabak.testutil.FakeCategoryRepository
import com.hisabak.testutil.MainDispatcherTest
import com.hisabak.testutil.TestClock
import com.hisabak.testutil.brand
import com.hisabak.testutil.category
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import com.hisabak.testutil.FakeTransactionRepository
import com.hisabak.feature.transaction.domain.usecase.ReassignBrandTransactionsUseCase
import com.hisabak.feature.transaction.domain.usecase.ObserveTransactionsUseCase
import com.hisabak.feature.brand.domain.usecase.ObserveBrandsUseCase
import com.hisabak.feature.brand.domain.usecase.DeleteBrandUseCase
import kotlin.test.assertNotNull
import com.hisabak.feature.category.domain.CategoryColor

@OptIn(ExperimentalCoroutinesApi::class)
class BrandEditViewModelTest : MainDispatcherTest() {

    private val clock = TestClock()
    private val brandRepo = FakeBrandRepository()
    private val txRepo = FakeTransactionRepository()
    private val catRepo = FakeCategoryRepository(listOf(category(id = "c1", name = "Food")))
    private val analytics = FakeAnalytics()
    private val categoryCreatedBus = CategoryCreatedBus()
    private val suggester = FakeAiCategorySuggester()

    private fun viewModel(brandId: BrandId? = null) = BrandEditViewModel(
        brandId = brandId,
        brandRepository = brandRepo,
        observeCategories = ObserveCategoriesUseCase(catRepo),
        createBrand = CreateBrandUseCase(brandRepo),
        updateBrand = UpdateBrandUseCase(brandRepo),
        deleteBrand = DeleteBrandUseCase(brandRepo),
        reassignBrandTransactions = ReassignBrandTransactionsUseCase(txRepo),
        observeBrands = ObserveBrandsUseCase(brandRepo),
        observeTransactions = ObserveTransactionsUseCase(txRepo),
        categoryCreatedBus = categoryCreatedBus,
        suggestCategory = SuggestBrandCategoryUseCase(suggester, catRepo, analytics),
        analytics = analytics,
    )

    @Test
    fun `category options come from the observed categories`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("Food"), vm.state.value.categoryOptions.map { it.name })
    }

    @Test
    fun `a blank name is rejected`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(BrandEditIntent.NameChanged("   "))

        vm.onIntent(BrandEditIntent.Save)
        advanceUntilIdle()

        assertTrue(vm.state.value.nameError != null)
        assertTrue(brandRepo.current.isEmpty())
        assertNull(vm.effect.value)
    }

    @Test
    fun `a valid new brand is created and emits Saved`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(BrandEditIntent.NameChanged("Carrefour"))
        vm.onIntent(BrandEditIntent.CategoryChanged(CategoryId("c1")))

        vm.onIntent(BrandEditIntent.Save)
        advanceUntilIdle()

        val saved = brandRepo.current.single()
        assertEquals("Carrefour", saved.name)
        assertEquals(CategoryId("c1"), saved.categoryId)
        // Saved carries the id so the transaction sheet's "New brand" detour can select it.
        assertEquals(BrandEditEffect.Saved(saved.id), vm.effect.value)

        val event = analytics.logged.single() as AnalyticsEvent.BrandCreated
        assertEquals(true, event.params["has_category"])
        // PII guard: the brand name never reaches analytics.
        assertTrue(event.params.values.none { it == "Carrefour" })
    }

    @Test
    fun `a category created via the new-category detour is selected and the bus is consumed`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(BrandEditIntent.NameChanged("Carrefour"))

        categoryCreatedBus.publish(CategoryId("c2"))
        advanceUntilIdle()

        assertEquals(CategoryId("c2"), vm.state.value.selectedCategoryId)
        assertEquals("Carrefour", vm.state.value.nameInput) // editor state survived the detour
        assertNull(categoryCreatedBus.pending.value)
    }

    @Test
    fun `a category id published before the editor opens is not lost`() = runTest {
        categoryCreatedBus.publish(CategoryId("c2"))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(CategoryId("c2"), vm.state.value.selectedCategoryId)
        assertNull(categoryCreatedBus.pending.value)
    }

    @Test
    fun `a settled name yields a suggestion after the debounce`() = runTest {
        suggester.result = AiRawCategorySuggestion("Food", null, null, null)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(BrandEditIntent.NameChanged("Carrefour"))
        advanceUntilIdle()

        val suggestion = vm.state.value.suggestion as CategorySuggestion.Existing
        assertEquals("c1", suggestion.category.id.value)
        assertEquals(false, vm.state.value.isSuggesting)
        assertEquals(listOf("Carrefour"), suggester.suggestedBrands) // one inference, not per keystroke
    }

    @Test
    fun `rapid typing runs one inference for the final name`() = runTest {
        suggester.result = AiRawCategorySuggestion("Food", null, null, null)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(BrandEditIntent.NameChanged("Ca"))
        vm.onIntent(BrandEditIntent.NameChanged("Carr"))
        vm.onIntent(BrandEditIntent.NameChanged("Carrefour"))
        advanceUntilIdle()

        assertEquals(listOf("Carrefour"), suggester.suggestedBrands)
    }

    @Test
    fun `no suggestion when the model is unavailable or a category is selected`() = runTest {
        suggester.result = AiRawCategorySuggestion("Food", null, null, null)
        val vm = viewModel()
        advanceUntilIdle()

        suggester.ready = false
        vm.onIntent(BrandEditIntent.NameChanged("Carrefour"))
        advanceUntilIdle()
        assertNull(vm.state.value.suggestion)

        suggester.ready = true
        vm.onIntent(BrandEditIntent.CategoryChanged(CategoryId("c1")))
        vm.onIntent(BrandEditIntent.NameChanged("Carrefour Market"))
        advanceUntilIdle()
        assertNull(vm.state.value.suggestion)
        assertTrue(suggester.suggestedBrands.isEmpty())
    }

    @Test
    fun `accepting an existing suggestion selects it`() = runTest {
        suggester.result = AiRawCategorySuggestion("Food", null, null, null)
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(BrandEditIntent.NameChanged("Carrefour"))
        advanceUntilIdle()

        vm.onIntent(BrandEditIntent.SuggestionAccepted)

        assertEquals(CategoryId("c1"), vm.state.value.selectedCategoryId)
        assertNull(vm.state.value.suggestion)
    }

    @Test
    fun `accepting a new suggestion opens the prefilled category editor`() = runTest {
        suggester.result = AiRawCategorySuggestion(null, "Pharmacy", "expenses", "heart")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(BrandEditIntent.NameChanged("Life Pharmacy"))
        advanceUntilIdle()

        vm.onIntent(BrandEditIntent.SuggestionAccepted)

        val effect = vm.effect.value as BrandEditEffect.OpenCategoryEditor
        assertEquals("Pharmacy", effect.prefill.name)
        assertNotNull(CategoryColor.hueOf(effect.prefill.color), "the prefill carries a derived hue")
        // The chip stays until the created category comes back through the bus.
        assertTrue(vm.state.value.suggestion is CategorySuggestion.New)

        categoryCreatedBus.publish(CategoryId("c9"))
        advanceUntilIdle()
        assertEquals(CategoryId("c9"), vm.state.value.selectedCategoryId)
        assertNull(vm.state.value.suggestion)
    }

    @Test
    fun `opening an uncategorized brand suggests without editing the name`() = runTest {
        suggester.result = AiRawCategorySuggestion("Food", null, null, null)
        brandRepo.emit(listOf(brand(id = "b1", name = "Carrefour", categoryId = null)))

        val vm = viewModel(BrandId("b1"))
        advanceUntilIdle()

        assertEquals("c1", ((vm.state.value.suggestion) as CategorySuggestion.Existing).category.id.value)
        assertEquals(listOf("Carrefour"), suggester.suggestedBrands)
    }

    @Test
    fun `opening a categorized brand never suggests`() = runTest {
        suggester.result = AiRawCategorySuggestion("Food", null, null, null)
        brandRepo.emit(listOf(brand(id = "b1", name = "Carrefour", categoryId = CategoryId("c1"))))

        val vm = viewModel(BrandId("b1"))
        advanceUntilIdle()

        assertNull(vm.state.value.suggestion)
        assertTrue(suggester.suggestedBrands.isEmpty())
    }

    @Test
    fun `editing the name clears a stale suggestion`() = runTest {
        suggester.result = AiRawCategorySuggestion("Food", null, null, null)
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(BrandEditIntent.NameChanged("Carrefour"))
        advanceUntilIdle()
        assertTrue(vm.state.value.suggestion != null)

        suggester.result = null
        vm.onIntent(BrandEditIntent.NameChanged("Carrefour X"))
        assertNull(vm.state.value.suggestion)
    }

    @Test
    fun `editing an existing brand loads then updates it`() = runTest {
        brandRepo.emit(listOf(brand(id = "b1", name = "Old", categoryId = null)))
        val vm = viewModel(BrandId("b1"))
        advanceUntilIdle()

        assertEquals("Old", vm.state.value.nameInput)
        assertEquals(false, vm.state.value.isNew) // titled "Edit brand", not "New brand"

        vm.onIntent(BrandEditIntent.NameChanged("New"))
        vm.onIntent(BrandEditIntent.CategoryChanged(CategoryId("c1")))
        vm.onIntent(BrandEditIntent.Save)
        advanceUntilIdle()

        val updated = brandRepo.current.single()
        assertEquals("b1", updated.id.value)
        assertEquals("New", updated.name)
        assertEquals(CategoryId("c1"), updated.categoryId)
    }
}
