package com.hisabak.feature.insights.presentation

import com.hisabak.core.common.SummaryPeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Carries a period from the dashboard's Review card to the Insights tab.
 *
 * The tab is a top-level destination, so it cannot take the period as a key argument the way the
 * old full-screen child did. Same shape as `TransactionListFilterBus`: the sender parks a request,
 * the screen consumes it once and keeps its own period from then on.
 */
class InsightsPeriodBus {
    private val _pending = MutableStateFlow<SummaryPeriod?>(null)
    val pending: StateFlow<SummaryPeriod?> = _pending.asStateFlow()

    fun request(period: SummaryPeriod) {
        _pending.value = period
    }

    fun consume() {
        _pending.value = null
    }
}
