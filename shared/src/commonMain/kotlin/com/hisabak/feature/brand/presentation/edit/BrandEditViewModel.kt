package com.hisabak.feature.brand.presentation.edit

import androidx.lifecycle.viewModelScope
import com.hisabak.core.common.DomainResult
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.core.presentation.BaseViewModel
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.brand.domain.BrandRepository
import com.hisabak.feature.brand.domain.ai.CategorySuggestion
import com.hisabak.feature.brand.domain.ai.SuggestBrandCategoryUseCase
import com.hisabak.feature.brand.domain.usecase.CreateBrandUseCase
import com.hisabak.feature.brand.domain.usecase.UpdateBrandUseCase
import com.hisabak.feature.category.domain.usecase.ObserveCategoriesUseCase
import com.hisabak.feature.category.presentation.CategoryCreatedBus
import com.hisabak.feature.category.presentation.edit.CategoryEditPrefill
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BrandEditViewModel(
    private val brandId: BrandId?,
    private val brandRepository: BrandRepository,
    private val observeCategories: ObserveCategoriesUseCase,
    private val createBrand: CreateBrandUseCase,
    private val updateBrand: UpdateBrandUseCase,
    private val categoryCreatedBus: CategoryCreatedBus,
    private val suggestCategory: SuggestBrandCategoryUseCase,
    private val analytics: Analytics,
) : BaseViewModel<BrandEditIntent, BrandEditUiState, BrandEditEffect>() {

    private var suggestJob: Job? = null

    override fun initialState() = BrandEditUiState(isNew = brandId == null)

    init {
        viewModelScope.launch {
            observeCategories().collect { categories ->
                val options = categories.map {
                    BrandEditUiState.CategoryOption(it.id, it.name, it.color)
                }
                setState { copy(categoryOptions = options) }
            }
        }
        // A category created via the "+ New category" detour lands here — select it.
        viewModelScope.launch {
            categoryCreatedBus.pending.collect { created ->
                if (created != null) {
                    setState { copy(selectedCategoryId = created, suggestion = null) }
                    categoryCreatedBus.consume()
                }
            }
        }
        if (brandId != null) loadExisting(brandId)
    }

    override fun onIntent(intent: BrandEditIntent) {
        when (intent) {
            is BrandEditIntent.NameChanged -> {
                // isSuggesting resets too: cancelling an in-flight request must not strand the spinner.
                setState { copy(nameInput = intent.value, nameError = null, suggestion = null, isSuggesting = false) }
                scheduleSuggestion(intent.value)
            }
            is BrandEditIntent.CategoryChanged ->
                setState { copy(selectedCategoryId = intent.categoryId) }
            BrandEditIntent.SuggestionAccepted -> acceptSuggestion()
            BrandEditIntent.Save -> save()
            BrandEditIntent.ConsumeEffect -> clearEffect()
        }
    }

    private fun scheduleSuggestion(name: String) {
        suggestJob?.cancel()
        val trimmed = name.trim()
        if (trimmed.length < MIN_SUGGEST_LENGTH) return
        suggestJob = viewModelScope.launch {
            delay(SUGGEST_DEBOUNCE_MS)
            if (state.value.selectedCategoryId != null || !suggestCategory.isAvailable()) return@launch
            setState { copy(isSuggesting = true) }
            val suggestion = suggestCategory(trimmed)
            setState { copy(isSuggesting = false, suggestion = suggestion) }
        }
    }

    private fun acceptSuggestion() {
        when (val suggestion = state.value.suggestion) {
            is CategorySuggestion.Existing -> {
                analytics.log(AnalyticsEvent.AiCategoryAccepted("existing"))
                setState { copy(selectedCategoryId = suggestion.category.id, suggestion = null) }
            }
            // The suggestion stays visible: cancelling the prefilled editor returns unchanged,
            // while a save comes back through the bus and clears it there.
            is CategorySuggestion.New -> {
                analytics.log(AnalyticsEvent.AiCategoryAccepted("new"))
                sendEffect(
                    BrandEditEffect.OpenCategoryEditor(
                        CategoryEditPrefill(suggestion.name, suggestion.type, suggestion.color, suggestion.icon),
                    ),
                )
            }
            null -> Unit
        }
    }

    private fun loadExisting(id: BrandId) {
        setState { copy(isLoading = true) }
        viewModelScope.launch {
            when (val result = brandRepository.getById(id)) {
                is DomainResult.Success -> {
                    val b = result.value
                    setState {
                        copy(
                            isLoading = false,
                            nameInput = b.name,
                            selectedCategoryId = b.categoryId,
                        )
                    }
                    // Categorizing an existing brand (the notification detour) opens with the
                    // name already filled — suggest without waiting for an edit.
                    if (b.categoryId == null) scheduleSuggestion(b.name)
                }
                is DomainResult.Failure -> setState {
                    copy(isLoading = false, generalError = result.error.message)
                }
            }
        }
    }

    private fun save() {
        val s = state.value
        val name = s.nameInput.trim()
        if (name.isEmpty()) {
            setState { copy(nameError = "Name is required") }
            return
        }
        setState { copy(isSaving = true, generalError = null) }
        viewModelScope.launch {
            val result: DomainResult<BrandId> = if (brandId == null) {
                createBrand(name = name, categoryId = s.selectedCategoryId).map { it.id }
            } else {
                when (val existing = brandRepository.getById(brandId)) {
                    is DomainResult.Success -> updateBrand(
                        existing.value.copy(name = name, categoryId = s.selectedCategoryId),
                    ).map { brandId }
                    is DomainResult.Failure -> DomainResult.Failure(existing.error)
                }
            }

            when (result) {
                is DomainResult.Success -> {
                    if (brandId == null) {
                        analytics.log(AnalyticsEvent.BrandCreated(hasCategory = s.selectedCategoryId != null))
                    }
                    setState { copy(isSaving = false) }
                    sendEffect(BrandEditEffect.Saved(result.value))
                }
                is DomainResult.Failure -> setState {
                    copy(isSaving = false, generalError = result.error.message)
                }
            }
        }
    }

    private companion object {
        const val MIN_SUGGEST_LENGTH = 2
        const val SUGGEST_DEBOUNCE_MS = 700L
    }
}
