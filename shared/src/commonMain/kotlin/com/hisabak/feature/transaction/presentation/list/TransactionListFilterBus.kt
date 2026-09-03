package com.hisabak.feature.transaction.presentation.list

import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.category.domain.CategoryId
import kotlinx.coroutines.flow.MutableStateFlow

sealed interface TransactionListFilterRequest {
    /** Show only transactions whose brand has no category. */
    data object Uncategorized : TransactionListFilterRequest

    /** Show only this brand's transactions — the "view transactions" action on a brand row. */
    data class ByBrand(val id: BrandId) : TransactionListFilterRequest

    /** Show only this category's transactions — the same action on a category row. */
    data class ByCategory(val id: CategoryId) : TransactionListFilterRequest
}

/**
 * One-shot bridge for asking the transactions list to apply a filter from elsewhere
 * (e.g. the dashboard's uncategorized card). The list ViewModel consumes the pending
 * request once and clears it, so it applies even when the list tab is created afterwards.
 */
class TransactionListFilterBus {
    val pending = MutableStateFlow<TransactionListFilterRequest?>(null)

    fun request(request: TransactionListFilterRequest) {
        pending.value = request
    }

    fun consume() {
        pending.value = null
    }
}
