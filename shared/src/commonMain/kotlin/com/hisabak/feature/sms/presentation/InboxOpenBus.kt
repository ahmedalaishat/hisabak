package com.hisabak.feature.sms.presentation

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Launch-time request to land on the SMS inbox — set by the iOS "Open SMS inbox" App Intent
 * (which may fire before the UI exists), consumed by the nav shell once composed. Same
 * pattern as [com.hisabak.feature.transaction.presentation.list.TransactionListFilterBus].
 */
class InboxOpenBus {
    val pending = MutableStateFlow(false)

    fun request() {
        pending.value = true
    }

    fun consume() {
        pending.value = false
    }
}
