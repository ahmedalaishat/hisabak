package com.hisabak.core.presentation

import com.hisabak.core.common.Clock
import com.hisabak.core.common.SummaryPeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one period the dashboard, the insights review, and the transaction list all show. A process
 * singleton, so going back to March 2024 on one tab shows March 2024 on the others — the screens
 * answer different questions about the same stretch of time, and three selections that drift apart
 * made their numbers disagree.
 *
 * Not persisted: a cold start opens on this month, which is what the app is opened to check.
 */
class PeriodSelection(clock: Clock) {
    private val _period = MutableStateFlow(SummaryPeriod.thisMonth(clock.today()))
    val period: StateFlow<SummaryPeriod> = _period.asStateFlow()

    fun select(period: SummaryPeriod) {
        _period.value = period
    }
}
