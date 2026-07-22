package com.hisabak.feature.category.presentation.edit

import com.hisabak.core.presentation.ViewEffect
import com.hisabak.core.presentation.ViewIntent
import com.hisabak.core.presentation.ViewState
import com.hisabak.feature.category.domain.CategoryType

data class CategoryEditUiState(
    val nameInput: String = "",
    val type: CategoryType = CategoryType.EXPENSES,
    val color: String = "gray",
    val icon: String = "wallet",
    val limitInput: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isNew: Boolean = true,
    val nameError: String? = null,
    val limitError: String? = null,
    val generalError: String? = null,
) : ViewState {
    val canSave: Boolean get() = !isSaving && nameInput.isNotBlank()
    val showLimit: Boolean get() = type == CategoryType.EXPENSES
}

sealed interface CategoryEditIntent : ViewIntent {
    data class NameChanged(val value: String) : CategoryEditIntent
    data class TypeChanged(val value: CategoryType) : CategoryEditIntent
    data class ColorChanged(val value: String) : CategoryEditIntent
    data class IconChanged(val value: String) : CategoryEditIntent
    data class LimitChanged(val value: String) : CategoryEditIntent
    data object Save : CategoryEditIntent
    data object ConsumeEffect : CategoryEditIntent
}

sealed interface CategoryEditEffect : ViewEffect {
    data object Saved : CategoryEditEffect
}
