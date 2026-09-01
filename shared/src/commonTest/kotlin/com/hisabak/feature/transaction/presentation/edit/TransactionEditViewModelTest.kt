package com.hisabak.feature.transaction.presentation.edit

import com.hisabak.core.common.Currency
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.brand.domain.usecase.ObserveBrandsUseCase
import com.hisabak.feature.brand.presentation.BrandCreatedBus
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.feature.category.domain.usecase.ObserveCategoriesUseCase
import com.hisabak.feature.transaction.domain.TransactionId
import com.hisabak.feature.transaction.domain.usecase.CreateTransactionUseCase
import com.hisabak.feature.transaction.domain.usecase.DeleteTransactionUseCase
import com.hisabak.feature.transaction.domain.usecase.UpdateTransactionUseCase
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.testutil.FakeAnalytics
import com.hisabak.testutil.FakeBrandRepository
import com.hisabak.testutil.FakeCategoryRepository
import com.hisabak.testutil.FakeSmsRepository
import com.hisabak.testutil.FakeTransactionRepository
import com.hisabak.testutil.MainDispatcherTest
import com.hisabak.testutil.TestClock
import com.hisabak.testutil.brand
import com.hisabak.testutil.category
import com.hisabak.testutil.smsMessage
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
            brand(id = "b-sav", name = "Omar", categoryId = CategoryId("c-sav")),
            brand(id = "b-uncat", name = "Starbucks", categoryId = null),
        ),
    )
    private val catRepo = FakeCategoryRepository(
        listOf(
            category(id = "c-exp", type = CategoryType.EXPENSES),
            category(id = "c-inc", type = CategoryType.INCOME),
            category(id = "c-sav", type = CategoryType.SAVINGS),
        ),
    )

    private val analytics = FakeAnalytics()
    private val smsRepo = FakeSmsRepository()
    private val draftBus = TransactionDraftBus()
    private val brandCreatedBus = BrandCreatedBus()

    private fun viewModel(transactionId: TransactionId? = null) = TransactionEditViewModel(
        transactionId = transactionId,
        currency = Currency.AED,
        clock = clock,
        transactionRepository = txRepo,
        observeBrands = ObserveBrandsUseCase(brandRepo),
        observeCategories = ObserveCategoriesUseCase(catRepo),
        createTransaction = CreateTransactionUseCase(txRepo, clock),
        updateTransaction = UpdateTransactionUseCase(txRepo),
        deleteTransaction = DeleteTransactionUseCase(txRepo, smsRepo),
        draftBus = draftBus,
        brandCreatedBus = brandCreatedBus,
        analytics = analytics,
    )

    @Test
    fun `a brand detour parks the typed input and the reopened sheet restores it`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(TransactionEditIntent.AmountChanged("42.50"))
        vm.onIntent(TransactionEditIntent.NoteChanged("lunch"))

        vm.onIntent(TransactionEditIntent.CreateBrandRequested)
        advanceUntilIdle()

        assertEquals(TransactionEditEffect.OpenBrandEditor(null), vm.effect.value)
        assertEquals("42.50", draftBus.pending.value?.amountInput)

        // The sheet reopens as a fresh ViewModel — the draft restores the typed input.
        val reopened = viewModel()
        advanceUntilIdle()
        assertEquals("42.50", reopened.state.value.amountInput)
        assertEquals("lunch", reopened.state.value.noteInput)
        assertNull(draftBus.pending.value)
    }

    @Test
    fun `a draft for another transaction is left alone`() = runTest {
        txRepo.emit(listOf(transaction(id = "t1", brandId = "b-exp", amountMinor = 10_00)))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(TransactionEditIntent.AmountChanged("42.50"))
        vm.onIntent(TransactionEditIntent.CreateBrandRequested)
        advanceUntilIdle()

        // A different sheet (editing t1) must not consume the new-entry draft.
        val other = viewModel(TransactionId("t1"))
        advanceUntilIdle()
        assertEquals("10.00", other.state.value.amountInput)
        assertEquals("42.50", draftBus.pending.value?.amountInput)
    }

    @Test
    fun `a created brand is selected and the type follows its category`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(CategoryType.EXPENSES, vm.state.value.selectedType)

        brandCreatedBus.publish(BrandId("b-inc"))
        advanceUntilIdle()

        assertEquals(BrandId("b-inc"), vm.state.value.selectedBrandId)
        assertEquals(CategoryType.INCOME, vm.state.value.selectedType)
        assertNull(brandCreatedBus.pending.value)
    }

    @Test
    fun `a created uncategorized brand is selected without changing the type`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        brandCreatedBus.publish(BrandId("b-uncat"))
        advanceUntilIdle()

        assertEquals(BrandId("b-uncat"), vm.state.value.selectedBrandId)
        assertEquals(CategoryType.EXPENSES, vm.state.value.selectedType)
    }

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

        assertTrue(vm.state.value.amountInvalid)
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

        assertTrue(vm.state.value.amountInvalid)
        assertTrue(txRepo.current.isEmpty())
    }

    @Test
    fun `saving without a brand sets a brand error`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(TransactionEditIntent.AmountChanged("50.00"))

        vm.onIntent(TransactionEditIntent.Save)
        advanceUntilIdle()

        assertTrue(vm.state.value.brandMissing)
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

    @Test
    fun `a savings withdrawal saves a negative amount`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(TransactionEditIntent.TypeSelected(CategoryType.SAVINGS))
        vm.onIntent(TransactionEditIntent.DirectionChanged(withdrawal = true))
        vm.onIntent(TransactionEditIntent.AmountChanged("1000.00"))
        vm.onIntent(TransactionEditIntent.BrandSelected(BrandId("b-sav")))

        vm.onIntent(TransactionEditIntent.Save)
        advanceUntilIdle()

        assertEquals(-100_000L, txRepo.current.single().amount.amountMinor)
        assertEquals(TransactionEditEffect.Saved, vm.effect.value)
    }

    @Test
    fun `a savings deposit stays positive`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(TransactionEditIntent.TypeSelected(CategoryType.SAVINGS))
        vm.onIntent(TransactionEditIntent.AmountChanged("2000.00"))
        vm.onIntent(TransactionEditIntent.BrandSelected(BrandId("b-sav")))

        vm.onIntent(TransactionEditIntent.Save)
        advanceUntilIdle()

        assertEquals(200_000L, txRepo.current.single().amount.amountMinor)
    }

    @Test
    fun `editing a withdrawal loads a positive input with the toggle on and keeps the sign`() = runTest {
        txRepo.emit(listOf(transaction(id = "t-wd", amountMinor = -100_000L, brandId = "b-sav")))
        val vm = viewModel(TransactionId("t-wd"))
        advanceUntilIdle()

        assertEquals("1000.00", vm.state.value.amountInput)
        assertTrue(vm.state.value.isWithdrawal)
        assertEquals(CategoryType.SAVINGS, vm.state.value.selectedType)

        // Saving without touching the direction keeps the withdrawal negative.
        vm.onIntent(TransactionEditIntent.Save)
        advanceUntilIdle()

        assertEquals(-100_000L, txRepo.current.single().amount.amountMinor)
    }

    @Test
    fun `switching type away from savings clears the withdrawal direction`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(TransactionEditIntent.TypeSelected(CategoryType.SAVINGS))
        vm.onIntent(TransactionEditIntent.DirectionChanged(withdrawal = true))

        vm.onIntent(TransactionEditIntent.TypeSelected(CategoryType.EXPENSES))
        vm.onIntent(TransactionEditIntent.AmountChanged("50.00"))
        vm.onIntent(TransactionEditIntent.BrandSelected(BrandId("b-exp")))
        vm.onIntent(TransactionEditIntent.Save)
        advanceUntilIdle()

        assertEquals(false, vm.state.value.isWithdrawal)
        assertEquals(5_000L, txRepo.current.single().amount.amountMinor) // an expense never saves negative
    }

    @Test
    fun `a withdrawal keeps its sign when its brand has lost its category`() = runTest {
        // The brand was re-categorized to None after the withdrawal was recorded: the type is
        // unknown, so re-saving must preserve the stored sign, not silently flip it positive.
        txRepo.emit(listOf(transaction(id = "t-wd", amountMinor = -100_000L, brandId = "b-uncat")))
        val vm = viewModel(TransactionId("t-wd"))
        advanceUntilIdle()

        vm.onIntent(TransactionEditIntent.Save)
        advanceUntilIdle()

        assertEquals(-100_000L, txRepo.current.single().amount.amountMinor)
    }

    @Test
    fun `zero is rejected even as a withdrawal`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(TransactionEditIntent.TypeSelected(CategoryType.SAVINGS))
        vm.onIntent(TransactionEditIntent.DirectionChanged(withdrawal = true))
        vm.onIntent(TransactionEditIntent.AmountChanged("0"))
        vm.onIntent(TransactionEditIntent.BrandSelected(BrandId("b-sav")))

        vm.onIntent(TransactionEditIntent.Save)
        advanceUntilIdle()

        assertTrue(vm.state.value.amountInvalid)
        assertTrue(txRepo.current.isEmpty())
    }

    @Test
    fun `delete is confirmed before it runs`() = runTest {
        txRepo.emit(listOf(transaction(id = "t1", brandId = "b-exp")))
        val vm = viewModel(TransactionId("t1"))
        advanceUntilIdle()

        vm.onIntent(TransactionEditIntent.DeleteRequested)
        advanceUntilIdle()

        // Asking is not doing: the row survives until the dialog is confirmed.
        assertTrue(vm.state.value.showDeleteConfirm)
        assertEquals(1, txRepo.current.size)
        assertNull(vm.effect.value)

        vm.onIntent(TransactionEditIntent.DeleteDismissed)
        advanceUntilIdle()

        assertEquals(false, vm.state.value.showDeleteConfirm)
        assertEquals(1, txRepo.current.size)
        assertTrue(analytics.logged.isEmpty(), "a dismissed dialog must not log a delete")
    }

    @Test
    fun `confirming delete removes the transaction and emits Deleted`() = runTest {
        txRepo.emit(listOf(transaction(id = "t1", brandId = "b-exp")))
        val vm = viewModel(TransactionId("t1"))
        advanceUntilIdle()

        vm.onIntent(TransactionEditIntent.DeleteRequested)
        vm.onIntent(TransactionEditIntent.DeleteConfirmed)
        advanceUntilIdle()

        assertTrue(txRepo.current.isEmpty())
        assertEquals(false, vm.state.value.showDeleteConfirm)
        assertEquals(false, vm.state.value.isDeleting)
        assertEquals(TransactionEditEffect.Deleted, vm.effect.value)
        assertEquals(listOf("transaction_deleted"), analytics.names())
    }

    @Test
    fun `deleting a captured transaction returns its SMS to the inbox`() = runTest {
        smsRepo.upsert(
            smsMessage(id = "s1", body = "Purchase of AED 10.00 at Carrefour")
                .copy(transactionId = TransactionId("t1")),
        )
        txRepo.emit(listOf(transaction(id = "t1", brandId = "b-exp", sourceSmsId = "s1")))
        val vm = viewModel(TransactionId("t1"))
        advanceUntilIdle()

        // The confirm copy warns that the message comes back.
        assertTrue(vm.state.value.fromSms)

        vm.onIntent(TransactionEditIntent.DeleteConfirmed)
        advanceUntilIdle()

        // The message survives, but no longer points at the deleted row — so the inbox
        // shows it as importable again rather than Linked-to-nothing.
        val message = smsRepo.current.single()
        assertNull(message.transactionId)
        assertEquals(false, message.isLinked)
    }

    @Test
    fun `a new transaction has nothing to delete`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(TransactionEditIntent.DeleteConfirmed)
        advanceUntilIdle()

        assertEquals(false, vm.state.value.isDeleting)
        assertNull(vm.effect.value)
        assertTrue(analytics.logged.isEmpty())
    }
}
