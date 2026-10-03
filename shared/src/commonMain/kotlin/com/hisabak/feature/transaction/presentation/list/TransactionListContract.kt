package com.hisabak.feature.transaction.presentation.list

import com.hisabak.core.common.Money
import com.hisabak.core.common.SummaryPeriod
import com.hisabak.core.presentation.ViewEffect
import com.hisabak.core.presentation.ViewIntent
import com.hisabak.core.presentation.ViewState
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.feature.transaction.domain.TransactionId
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

data class TransactionRow(
    val id: TransactionId,
    val amount: Money,
    val brandName: String,
    val categoryName: String?,
    val categoryType: CategoryType?,
    val categoryColor: String?,
    val categoryIcon: String?,
    val note: String?,
    val occurredAt: Instant,
)

data class BrandFilterOption(
    val id: BrandId,
    val name: String,
    /** The brand's category, so a long brand list still groups visually. */
    val categoryColor: String?,
    val categoryIcon: String?,
)

data class CategoryFilterOption(
    val id: CategoryId,
    val name: String,
    val color: String,
    val icon: String?,
)

/** Sentinel category id meaning "transactions whose brand has no category". */
val UncategorizedCategoryId = CategoryId("__uncategorized__")

data class TransactionListUiState(
    val rows: List<TransactionRow> = emptyList(),
    val search: String = "",
    val period: SummaryPeriod = SummaryPeriod.All,
    /** What the period bar's arrows need; null until the first load. */
    val today: LocalDate? = null,
    val earliest: LocalDate? = null,
    val summaryIncome: Long = 0L,
    val summaryExpenses: Long = 0L,
    /** The period's transactions, before the list filters — the "of 175" in "12 of 175". */
    val totalCount: Int = 0,
    val brandFilter: BrandId? = null,
    val categoryFilter: CategoryId? = null,
    val brandOptions: List<BrandFilterOption> = emptyList(),
    val categoryOptions: List<CategoryFilterOption> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
) : ViewState {
    val hasActiveFilters: Boolean
        get() = brandFilter != null || categoryFilter != null
    val selectedBrandName: String? get() = brandOptions.firstOrNull { it.id == brandFilter }?.name
    val selectedCategoryName: String? get() = categoryOptions.firstOrNull { it.id == categoryFilter }?.name
}

sealed interface TransactionListIntent : ViewIntent {
    data class SearchChanged(val query: String) : TransactionListIntent
    data class PeriodChanged(val period: SummaryPeriod) : TransactionListIntent
    data class BrandFilterChanged(val id: BrandId?) : TransactionListIntent
    data class CategoryFilterChanged(val id: CategoryId?) : TransactionListIntent
    data object ClearFilters : TransactionListIntent
    data object ConsumeEffect : TransactionListIntent
}

sealed interface TransactionListEffect : ViewEffect {
    // No one-shot effects yet. Add here as nav / snackbar needs appear.
}
