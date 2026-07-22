package com.hisabak.feature.transaction.presentation.list

import kotlinx.coroutines.flow.MutableStateFlow

sealed interface TransactionListFilterRequest {
    /** Show only transactions whose brand has no category. */
    data object Uncategorized : TransactionListFilterRequest
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
