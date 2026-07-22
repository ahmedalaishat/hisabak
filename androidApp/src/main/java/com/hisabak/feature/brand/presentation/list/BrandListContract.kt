package com.hisabak.feature.brand.presentation.list

import com.hisabak.core.presentation.ViewEffect
import com.hisabak.core.presentation.ViewIntent
import com.hisabak.core.presentation.ViewState
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.category.domain.CategoryId

data class BrandRow(
    val id: BrandId,
    val name: String,
    val categoryId: CategoryId?,
    val categoryName: String?,
    val categoryColor: String?,
    val categoryIcon: String?,
    val transactionCount: Int,
    val totalMinor: Long,
)

data class BrandListUiState(
    val rows: List<BrandRow> = emptyList(),
    val search: String = "",
    val categoryFilter: CategoryId? = null,
    val availableCategories: List<CategoryOption> = emptyList(),
    val isLoading: Boolean = true,
) : ViewState {
    data class CategoryOption(
        val id: CategoryId,
        val name: String,
        val color: String,
    )
}

sealed interface BrandListIntent : ViewIntent {
    data class SearchChanged(val query: String) : BrandListIntent
    data class CategoryFilterChanged(val categoryId: CategoryId?) : BrandListIntent
    data class Delete(val id: BrandId) : BrandListIntent
    /** Move [sourceId]'s transactions onto [targetId], then delete [sourceId]. */
    data class MergeAndDelete(val sourceId: BrandId, val targetId: BrandId) : BrandListIntent
    data object ConsumeEffect : BrandListIntent
}

sealed interface BrandListEffect : ViewEffect {
    data class Message(val text: String) : BrandListEffect
}
