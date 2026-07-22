package com.hisabak.feature.transaction.presentation.edit

import com.hisabak.core.common.Currency
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.brand.domain.usecase.ObserveBrandsUseCase
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.feature.category.domain.usecase.ObserveCategoriesUseCase
import com.hisabak.feature.transaction.domain.TransactionId
import com.hisabak.feature.transaction.domain.usecase.CreateTransactionUseCase
import com.hisabak.feature.transaction.domain.usecase.UpdateTransactionUseCase
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.testutil.FakeAnalytics
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
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionEditViewModelTest : MainDispatcherTest() {

    private val clock = TestClock()
    private val txRepo = FakeTransactionRepository()
    private val brandRepo = FakeBrandRepository(
        listOf(
            brand(id = "b-exp", name = "Carrefour", categoryId = CategoryId("c-exp")),
            brand(id = "b-inc", name = "Salary", categoryId = CategoryId("c-inc")),
            brand(id = "b-uncat", name = "Starbucks", categoryId = null),
        ),
    )
    private val catRepo = FakeCategoryRepository(
        listOf(
            category(id = "c-exp", type = CategoryType.EXPENSES),
            category(id = "c-inc", type = CategoryType.INCOME),
        ),
    )

    private val analytics = FakeAnalytics()

    private fun viewModel(transactionId: TransactionId? = null) = TransactionEditViewModel(
        transactionId = transactionId,
        currency = Currency.AED,
        clock = clock,
        transactionRepository = txRepo,
        observeBrands = ObserveBrandsUseCase(brandRepo),
        observeCategories = ObserveCategoriesUseCase(catRepo),
        createTransaction = CreateTransactionUseCase(txRepo, clock),
        updateTransaction = UpdateTransactionUseCase(txRepo),
        analytics = analytics,
    )

    @Test
    fun `brand options are filtered by the selected type`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        // Default type is EXPENSES -> only the expense brand is offered.
        assertEquals(listOf("Carrefour"), vm.state.value.brandOptions.map { it.name })

        vm.onIntent(TransactionEditIntent.TypeSelected(CategoryType.INCOME))
        advanceUntilIdle()
        assertEquals(listOf("Salary"), vm.state.value.brandOptions.map { it.name })
    }

    @Test
    fun `changing type clears the selected brand`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(TransactionEditIntent.BrandSelected(BrandId("b-exp")))
        assertEquals(BrandId("b-exp"), vm.state.value.selectedBrandId)

        vm.onIntent(TransactionEditIntent.TypeSelected(CategoryType.INCOME))

        assertNull(vm.state.value.selectedBrandId)
    }

    @Test
    fun `saving without an amount sets an amount error`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(TransactionEditIntent.BrandSelected(BrandId("b-exp")))

        vm.onIntent(TransactionEditIntent.Save)
        advanceUntilIdle()

        assertTrue(vm.state.value.amountError != null)
        assertTrue(txRepo.current.isEmpty())
        assertNull(vm.effect.value)
        assertTrue(analytics.logged.isEmpty(), "validation failure must not log analytics")
    }

    @Test
    fun `non-positive amounts are rejected`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(TransactionEditIntent.AmountChanged("0"))
        vm.onIntent(TransactionEditIntent.BrandSelected(BrandId("b-exp")))

        vm.onIntent(TransactionEditIntent.Save)
        advanceUntilIdle()

        assertTrue(vm.state.value.amountError != null)
        assertTrue(txRepo.current.isEmpty())
    }

    @Test
    fun `saving without a brand sets a brand error`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(TransactionEditIntent.AmountChanged("50.00"))

        vm.onIntent(TransactionEditIntent.Save)
        advanceUntilIdle()

        assertTrue(vm.state.value.brandError != null)
        assertTrue(txRepo.current.isEmpty())
    }

    @Test
    fun `a valid new transaction is created and emits Saved`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(TransactionEditIntent.AmountChanged("50.00"))
        vm.onIntent(TransactionEditIntent.BrandSelected(BrandId("b-exp")))
        vm.onIntent(TransactionEditIntent.NoteChanged("Groceries"))

        vm.onIntent(TransactionEditIntent.Save)
        advanceUntilIdle()

        val saved = txRepo.current.single()
        assertEquals(5_000L, saved.amount.amountMinor)
        assertEquals(BrandId("b-exp"), saved.brandId)
        assertEquals("Groceries", saved.note)
        assertEquals(TransactionEditEffect.Saved, vm.effect.value)

        val event = analytics.logged.single() as AnalyticsEvent.TransactionCreated
        assertEquals("transaction_created", event.name)
        assertEquals("manual", event.params["source"])
        assertEquals("50_200", event.params["amount_bucket"])
        assertEquals(true, event.params["has_note"])
        // PII guard: the raw amount and note text must never reach analytics.
        assertTrue(event.params.values.none { it == "Groceries" || it == 5_000L || it == "50.00" })
    }

    @Test
    fun `editing an existing transaction loads it and saves the update`() = runTest {
        txRepo.emit(listOf(transaction(id = "t1", amountMinor = 1_000L, brandId = "b-exp", note = "old")))
        val vm = viewModel(TransactionId("t1"))
        advanceUntilIdle()

        // Loaded into the form.
        assertEquals(BrandId("b-exp"), vm.state.value.selectedBrandId)
        assertEquals(CategoryType.EXPENSES, vm.state.value.selectedType)

        vm.onIntent(TransactionEditIntent.AmountChanged("25.00"))
        vm.onIntent(TransactionEditIntent.Save)
        advanceUntilIdle()

        val updated = txRepo.current.single()
        assertEquals("t1", updated.id.value)
        assertEquals(2_500L, updated.amount.amountMinor)
        assertEquals(TransactionEditEffect.Saved, vm.effect.value)
        assertEquals(listOf("transaction_edited"), analytics.names())
    }

    @Test
    fun `editing an uncategorized transaction shows and keeps its brand`() = runTest {
        // Captured-from-SMS transactions have an uncategorized brand that matches no type filter.
        txRepo.emit(listOf(transaction(id = "t-uncat", amountMinor = 1_000L, brandId = "b-uncat")))
        val vm = viewModel(TransactionId("t-uncat"))
        advanceUntilIdle()

        assertEquals(false, vm.state.value.isNew) // titled "Edit transaction", not "New transaction"
        assertEquals(BrandId("b-uncat"), vm.state.value.selectedBrandId)
        // The uncategorized brand is offered even though the default type filter is EXPENSES.
        assertTrue(vm.state.value.brandOptions.any { it.id == BrandId("b-uncat") })

        vm.onIntent(TransactionEditIntent.AmountChanged("25.00"))
        vm.onIntent(TransactionEditIntent.Save)
        advanceUntilIdle()

        val updated = txRepo.current.single()
        assertEquals(BrandId("b-uncat"), updated.brandId) // brand preserved, still uncategorized
        assertEquals(2_500L, updated.amount.amountMinor)
        assertEquals(TransactionEditEffect.Saved, vm.effect.value)
    }
}
