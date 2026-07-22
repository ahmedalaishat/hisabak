package com.hisabak.feature.brand.presentation.edit

import androidx.lifecycle.viewModelScope
import com.hisabak.core.common.DomainResult
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.core.presentation.BaseViewModel
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.brand.domain.BrandRepository
import com.hisabak.feature.brand.domain.usecase.CreateBrandUseCase
import com.hisabak.feature.brand.domain.usecase.UpdateBrandUseCase
import com.hisabak.feature.category.domain.usecase.ObserveCategoriesUseCase
import kotlinx.coroutines.launch

class BrandEditViewModel(
    private val brandId: BrandId?,
    private val brandRepository: BrandRepository,
    private val observeCategories: ObserveCategoriesUseCase,
    private val createBrand: CreateBrandUseCase,
    private val updateBrand: UpdateBrandUseCase,
    private val analytics: Analytics,
) : BaseViewModel<BrandEditIntent, BrandEditUiState, BrandEditEffect>() {

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
        if (brandId != null) loadExisting(brandId)
    }

    override fun onIntent(intent: BrandEditIntent) {
        when (intent) {
            is BrandEditIntent.NameChanged ->
                setState { copy(nameInput = intent.value, nameError = null) }
            is BrandEditIntent.CategoryChanged ->
                setState { copy(selectedCategoryId = intent.categoryId) }
            BrandEditIntent.Save -> save()
            BrandEditIntent.ConsumeEffect -> clearEffect()
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
            val result: DomainResult<Unit> = if (brandId == null) {
                createBrand(name = name, categoryId = s.selectedCategoryId).map { }
            } else {
                when (val existing = brandRepository.getById(brandId)) {
                    is DomainResult.Success -> updateBrand(
                        existing.value.copy(name = name, categoryId = s.selectedCategoryId),
                    )
                    is DomainResult.Failure -> DomainResult.Failure(existing.error)
                }
            }

            when (result) {
                is DomainResult.Success -> {
                    if (brandId == null) {
                        analytics.log(AnalyticsEvent.BrandCreated(hasCategory = s.selectedCategoryId != null))
                    }
                    setState { copy(isSaving = false) }
                    sendEffect(BrandEditEffect.Saved)
                }
                is DomainResult.Failure -> setState {
                    copy(isSaving = false, generalError = result.error.message)
                }
            }
        }
    }
}
