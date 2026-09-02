package com.hisabak.feature.category.presentation.edit

import com.hisabak.core.presentation.ViewEffect
import com.hisabak.core.presentation.ViewIntent
import com.hisabak.core.presentation.ViewState
import com.hisabak.feature.category.domain.CategoryColor
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryType

data class CategoryEditUiState(
    val nameInput: String = "",
    val type: CategoryType = CategoryType.EXPENSES,
    val color: String = "gray",
    val icon: String = "wallet",
    val limitInput: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val isNew: Boolean = true,
    val nameError: String? = null,
    val limitError: String? = null,
    val generalError: String? = null,
    /** Colors the other categories already use — shown in the picker so the choice has context. */
    val colorsInUse: List<UsedCategoryColor> = emptyList(),
) : ViewState {
    val canSave: Boolean get() = !isSaving && nameInput.isNotBlank()
    val showLimit: Boolean get() = type == CategoryType.EXPENSES

    /**
     * Another category already wearing this exact glyph, if any. Two categories with the same
     * icon are hard to tell apart in a grid, a chip row, and a notification tile alike.
     */
    val iconClash: String?
        get() = colorsInUse.firstOrNull { it.iconKey == icon }?.name

    /** The hue the custom picker opens on — the current color's, or a free one if it has none. */
    val pickerHue: Int
        get() = CategoryColor.hueFor(color)
            ?: CategoryColor.mostDistinctHue(colorsInUse.mapNotNull { CategoryColor.hueFor(it.colorKey) })
}

sealed interface CategoryEditIntent : ViewIntent {
    data class NameChanged(val value: String) : CategoryEditIntent
    data class TypeChanged(val value: CategoryType) : CategoryEditIntent
    data class ColorChanged(val value: String) : CategoryEditIntent
    data class IconChanged(val value: String) : CategoryEditIntent
    data class LimitChanged(val value: String) : CategoryEditIntent
    data object Save : CategoryEditIntent
    data object Delete : CategoryEditIntent
    data object ConsumeEffect : CategoryEditIntent
}

sealed interface CategoryEditEffect : ViewEffect {
    data class Saved(val id: CategoryId) : CategoryEditEffect
    data object Deleted : CategoryEditEffect
    data class DeleteFailed(val message: String) : CategoryEditEffect
}

/** Initial values for a new category opened from an accepted AI suggestion (already sanitized). */
data class CategoryEditPrefill(
    val name: String,
    val type: CategoryType,
    val color: String,
    val icon: String,
)
