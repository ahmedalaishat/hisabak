package com.hisabak.feature.transaction.presentation.edit

import androidx.lifecycle.viewModelScope
import com.hisabak.core.common.Clock
import com.hisabak.core.common.Currency
import com.hisabak.core.common.DomainResult
import com.hisabak.core.common.Money
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.core.presentation.BaseViewModel
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.brand.domain.usecase.ObserveBrandsUseCase
import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.feature.category.domain.usecase.ObserveCategoriesUseCase
import com.hisabak.feature.transaction.domain.TransactionId
import com.hisabak.feature.transaction.domain.TransactionRepository
import com.hisabak.feature.transaction.domain.usecase.CreateTransactionUseCase
import com.hisabak.feature.transaction.domain.usecase.UpdateTransactionUseCase
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class TransactionEditViewModel(
    private val transactionId: TransactionId?,
    private val currency: Currency,
    private val clock: Clock,
    private val transactionRepository: TransactionRepository,
    private val observeBrands: ObserveBrandsUseCase,
    private val observeCategories: ObserveCategoriesUseCase,
    private val createTransaction: CreateTransactionUseCase,
    private val updateTransaction: UpdateTransactionUseCase,
    private val analytics: Analytics,
) : BaseViewModel<TransactionEditIntent, TransactionEditUiState, TransactionEditEffect>() {

    override fun initialState() = TransactionEditUiState(isNew = transactionId == null)

    init {
        if (transactionId == null) setState { copy(occurredAt = clock.now()) }
        viewModelScope.launch {
            val selectedTypeFlow = state.map { it.selectedType }.distinctUntilChanged()
            val selectedBrandIdFlow = state.map { it.selectedBrandId }.distinctUntilChanged()
            combine(
                observeBrands(),
                observeCategories(),
                selectedTypeFlow,
                selectedBrandIdFlow,
            ) { brands, categories, type, selectedBrandId ->
                val colorById = categories.associate { it.id to it.color }
                val typeById = categories.associate { it.id to it.type }
                brands
                    // Brands of the chosen type, plus the transaction's current brand even if it
                    // doesn't match — e.g. an uncategorized brand captured from SMS — so editing
                    // an existing transaction always shows and keeps its brand.
                    .filter { brand -> brand.categoryId?.let(typeById::get) == type || brand.id == selectedBrandId }
                    .map { brand ->
                        TransactionEditUiState.BrandOption(
                            id = brand.id,
                            name = brand.name,
                            categoryColor = brand.categoryId?.let(colorById::get),
                        )
                    }
                    .sortedBy { it.name.lowercase() }
            }.collect { options ->
                setState { copy(brandOptions = options) }
            }
        }
        if (transactionId != null) loadExisting(transactionId)
    }

    override fun onIntent(intent: TransactionEditIntent) {
        when (intent) {
            is TransactionEditIntent.AmountChanged ->
                setState { copy(amountInput = intent.value, amountError = null) }
            is TransactionEditIntent.BrandSelected ->
                setState { copy(selectedBrandId = intent.brandId, brandError = null) }
            is TransactionEditIntent.NoteChanged ->
                setState { copy(noteInput = intent.value) }
            is TransactionEditIntent.TypeSelected ->
                setState { copy(selectedType = intent.type, selectedBrandId = null, brandError = null) }
            is TransactionEditIntent.DateChanged ->
                setState { copy(occurredAt = intent.instant, showDatePicker = false) }
            TransactionEditIntent.DatePickerOpened ->
                setState { copy(showDatePicker = true) }
            TransactionEditIntent.DatePickerDismissed ->
                setState { copy(showDatePicker = false) }
            TransactionEditIntent.Save -> save()
            TransactionEditIntent.ConsumeEffect -> clearEffect()
        }
    }

    private fun loadExisting(id: TransactionId) {
        setState { copy(isLoading = true) }
        viewModelScope.launch {
            when (val result = transactionRepository.getById(id)) {
                is DomainResult.Success -> {
                    val tx = result.value
                    val type = resolveBrandType(tx.brandId)
                    setState {
                        copy(
                            isLoading = false,
                            amountInput = formatAmountInput(tx.amount),
                            selectedBrandId = tx.brandId,
                            selectedType = type ?: selectedType,
                            noteInput = tx.note.orEmpty(),
                            occurredAt = tx.occurredAt,
                        )
                    }
                }
                is DomainResult.Failure -> setState {
                    copy(isLoading = false, generalError = result.error.message)
                }
            }
        }
    }

    private suspend fun resolveBrandType(brandId: BrandId): CategoryType? {
        val categoryId = observeBrands().first().firstOrNull { it.id == brandId }?.categoryId ?: return null
        return observeCategories().first().firstOrNull { it.id == categoryId }?.type
    }

    private fun save() {
        val s = state.value
        val money = Money.parseMajor(s.amountInput, currency)
        if (money == null || !money.isPositive) {
            setState { copy(amountError = "Enter a positive amount") }
            return
        }
        val brandId = s.selectedBrandId
        if (brandId == null) {
            setState { copy(brandError = "Pick a brand") }
            return
        }
        setState { copy(isSaving = true, generalError = null) }
        viewModelScope.launch {
            val note = s.noteInput.trim().ifEmpty { null }

            val result: DomainResult<Unit> = if (transactionId == null) {
                createTransaction(
                    amount = money,
                    brandId = brandId,
                    note = note,
                    occurredAt = s.occurredAt,
                ).map { }
            } else {
                when (val existing = transactionRepository.getById(transactionId)) {
                    is DomainResult.Success -> updateTransaction(
                        existing.value.copy(
                            amount = money,
                            brandId = brandId,
                            note = note,
                            occurredAt = s.occurredAt,
                        ),
                    )
                    is DomainResult.Failure -> DomainResult.Failure(existing.error)
                }
            }

            when (result) {
                is DomainResult.Success -> {
                    analytics.log(
                        if (transactionId == null) {
                            AnalyticsEvent.TransactionCreated(amount = money, hasNote = note != null)
                        } else {
                            AnalyticsEvent.TransactionEdited(amount = money)
                        },
                    )
                    setState { copy(isSaving = false) }
                    sendEffect(TransactionEditEffect.Saved)
                }
                is DomainResult.Failure -> setState {
                    copy(isSaving = false, generalError = result.error.message)
                }
            }
        }
    }
}

private fun formatAmountInput(money: Money): String {
    val whole = money.amountMinor / 100
    val frac = kotlin.math.abs(money.amountMinor % 100)
    return "$whole.${frac.toString().padStart(2, '0')}"
}
