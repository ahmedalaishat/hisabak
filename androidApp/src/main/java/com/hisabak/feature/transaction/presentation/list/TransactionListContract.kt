package com.hisabak.feature.transaction.presentation.list

import com.hisabak.R
import com.hisabak.core.common.Money
import com.hisabak.core.common.SummaryPeriod
import com.hisabak.core.presentation.ViewEffect
import com.hisabak.core.presentation.ViewIntent
import com.hisabak.core.presentation.ViewState
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.feature.transaction.domain.TransactionId
import java.time.Instant

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

data class BrandFilterOption(val id: BrandId, val name: String)

data class CategoryFilterOption(val id: CategoryId, val name: String, val color: String)

/** Sentinel category id meaning "transactions whose brand has no category". */
val UncategorizedCategoryId = CategoryId("__uncategorized__")

/** Quick rolling date windows for the transaction list (separate from the summary period). */
enum class DateRangeFilter(val labelRes: Int, val days: Long?) {
    ALL(R.string.date_all, null),
    LAST_7(R.string.date_last_7, 7),
    LAST_30(R.string.date_last_30, 30),
    LAST_90(R.string.date_last_90, 90),
}

data class TransactionListUiState(
    val rows: List<TransactionRow> = emptyList(),
    val search: String = "",
    val period: SummaryPeriod = SummaryPeriod.CURRENT_MONTH,
    val summaryIncome: Long = 0L,
    val summaryExpenses: Long = 0L,
    val brandFilter: BrandId? = null,
    val categoryFilter: CategoryId? = null,
    val dateRange: DateRangeFilter = DateRangeFilter.ALL,
    val brandOptions: List<BrandFilterOption> = emptyList(),
    val categoryOptions: List<CategoryFilterOption> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
) : ViewState {
    val hasActiveFilters: Boolean
        get() = brandFilter != null || categoryFilter != null || dateRange != DateRangeFilter.ALL
    val selectedBrandName: String? get() = brandOptions.firstOrNull { it.id == brandFilter }?.name
    val selectedCategoryName: String? get() = categoryOptions.firstOrNull { it.id == categoryFilter }?.name
}

sealed interface TransactionListIntent : ViewIntent {
    data class SearchChanged(val query: String) : TransactionListIntent
    data class PeriodChanged(val period: SummaryPeriod) : TransactionListIntent
    data class BrandFilterChanged(val id: BrandId?) : TransactionListIntent
    data class CategoryFilterChanged(val id: CategoryId?) : TransactionListIntent
    data class DateRangeChanged(val range: DateRangeFilter) : TransactionListIntent
    data object ClearFilters : TransactionListIntent
    data class Delete(val id: TransactionId) : TransactionListIntent
    data object ConsumeEffect : TransactionListIntent
}

sealed interface TransactionListEffect : ViewEffect {
    // No one-shot effects yet. Add here as nav / snackbar needs appear.
}
