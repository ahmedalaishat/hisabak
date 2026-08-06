package com.hisabak.feature.brand.presentation.edit

import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.brand.domain.usecase.CreateBrandUseCase
import com.hisabak.feature.brand.domain.usecase.UpdateBrandUseCase
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.usecase.ObserveCategoriesUseCase
import com.hisabak.feature.category.presentation.CategoryCreatedBus
import com.hisabak.core.domain.analytics.AnalyticsEvent
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

@OptIn(ExperimentalCoroutinesApi::class)
class BrandEditViewModelTest : MainDispatcherTest() {

    private val clock = TestClock()
    private val brandRepo = FakeBrandRepository()
    private val catRepo = FakeCategoryRepository(listOf(category(id = "c1", name = "Food")))
    private val analytics = FakeAnalytics()
    private val categoryCreatedBus = CategoryCreatedBus()

    private fun viewModel(brandId: BrandId? = null) = BrandEditViewModel(
        brandId = brandId,
        brandRepository = brandRepo,
        observeCategories = ObserveCategoriesUseCase(catRepo),
        createBrand = CreateBrandUseCase(brandRepo),
        updateBrand = UpdateBrandUseCase(brandRepo),
        categoryCreatedBus = categoryCreatedBus,
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
        assertEquals(BrandEditEffect.Saved, vm.effect.value)

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
