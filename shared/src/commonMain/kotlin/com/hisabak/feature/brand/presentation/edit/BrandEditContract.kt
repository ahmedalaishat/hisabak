package com.hisabak.feature.brand.presentation.edit

import com.hisabak.core.presentation.ViewEffect
import com.hisabak.core.presentation.ViewIntent
import com.hisabak.core.presentation.ViewState
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.brand.domain.ai.CategorySuggestion
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.presentation.edit.CategoryEditPrefill

data class BrandEditUiState(
    val nameInput: String = "",
    val selectedCategoryId: CategoryId? = null,
    val categoryOptions: List<CategoryOption> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isNew: Boolean = true,
    val nameError: String? = null,
    val generalError: String? = null,
    // AI category suggestion — only rendered while no category is selected.
    val isSuggesting: Boolean = false,
    val suggestion: CategorySuggestion? = null,
) : ViewState {
    data class CategoryOption(
        val id: CategoryId,
        val name: String,
        val color: String,
    )

    val canSave: Boolean get() = !isSaving && nameInput.isNotBlank() && selectedCategoryId != null
}

sealed interface BrandEditIntent : ViewIntent {
    data class NameChanged(val value: String) : BrandEditIntent
    data class CategoryChanged(val categoryId: CategoryId?) : BrandEditIntent
    data object SuggestionAccepted : BrandEditIntent
    data object Save : BrandEditIntent
    data object ConsumeEffect : BrandEditIntent
}

sealed interface BrandEditEffect : ViewEffect {
    data class Saved(val id: BrandId) : BrandEditEffect

    /** Accepted "new category" suggestion — open the category editor prefilled, in pick mode. */
    data class OpenCategoryEditor(val prefill: CategoryEditPrefill) : BrandEditEffect
}
