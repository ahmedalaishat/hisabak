package com.hisabak.feature.dashboard.presentation

import androidx.lifecycle.viewModelScope
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.core.presentation.BaseViewModel
import com.hisabak.core.presentation.PeriodSelection
import com.hisabak.feature.dashboard.domain.usecase.GetDashboardMetricsUseCase
import com.hisabak.feature.insights.domain.InsightsSummary
import com.hisabak.feature.insights.domain.deriveInsights
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val getMetrics: GetDashboardMetricsUseCase,
    private val analytics: Analytics,
    private val periodSelection: PeriodSelection,
) : BaseViewModel<DashboardIntent, DashboardUiState, DashboardEffect>() {

    override fun initialState() = DashboardUiState(period = periodSelection.period.value)

    init {
        // Paired with its period, so the review and the bar can never describe a different window
        // from the numbers — the selection is shared, and may change from another tab mid-compute.
        periodSelection.period
            .flatMapLatest { p -> getMetrics(flowOf(p)).map { p to it } }
            .onEach { (period, snapshot) ->
                // Derived here, in the same emission, so the card can never lag the numbers.
                val review = deriveInsights(InsightsSummary.from(snapshot, period))
                setState { copy(period = period, snapshot = snapshot, review = review, isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: DashboardIntent) {
        when (intent) {
            is DashboardIntent.PeriodChanged -> {
                periodSelection.select(intent.period)
                analytics.log(AnalyticsEvent.DashboardPeriodChanged(period = intent.period.kind))
            }
        }
    }
}
