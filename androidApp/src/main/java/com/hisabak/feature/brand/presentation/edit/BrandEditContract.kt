package com.hisabak.feature.brand.presentation.edit

import com.hisabak.core.presentation.ViewEffect
import com.hisabak.core.presentation.ViewIntent
import com.hisabak.core.presentation.ViewState
import com.hisabak.feature.category.domain.CategoryId

data class BrandEditUiState(
    val nameInput: String = "",
    val selectedCategoryId: CategoryId? = null,
    val categoryOptions: List<CategoryOption> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isNew: Boolean = true,
    val nameError: String? = null,
    val generalError: String? = null,
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
    data object Save : BrandEditIntent
    data object ConsumeEffect : BrandEditIntent
}

sealed interface BrandEditEffect : ViewEffect {
    data object Saved : BrandEditEffect
}
