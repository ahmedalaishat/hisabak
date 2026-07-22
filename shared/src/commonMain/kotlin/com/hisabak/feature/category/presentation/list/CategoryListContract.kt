package com.hisabak.feature.category.presentation.list

import com.hisabak.core.presentation.ViewEffect
import com.hisabak.core.presentation.ViewIntent
import com.hisabak.core.presentation.ViewState
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryType

data class CategoryRow(
    val id: CategoryId,
    val name: String,
    val type: CategoryType,
    val color: String,
    val icon: String,
    val transactionCount: Int,
    val totalMinor: Long,
)

data class CategoryListUiState(
    val rows: List<CategoryRow> = emptyList(),
    val search: String = "",
    val typeFilter: CategoryType? = null,
    val isLoading: Boolean = true,
) : ViewState

sealed interface CategoryListIntent : ViewIntent {
    data class SearchChanged(val query: String) : CategoryListIntent
    data class TypeFilterChanged(val type: CategoryType?) : CategoryListIntent
    data class Delete(val id: CategoryId) : CategoryListIntent
    data object ConsumeEffect : CategoryListIntent
}

sealed interface CategoryListEffect : ViewEffect {
    // Reserved for future snackbars / confirm dialogs.
}
