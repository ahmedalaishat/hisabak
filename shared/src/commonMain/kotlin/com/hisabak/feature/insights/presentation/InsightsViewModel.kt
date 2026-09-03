package com.hisabak.feature.insights.presentation

import androidx.lifecycle.viewModelScope
import com.hisabak.core.common.SummaryPeriod
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.core.presentation.BaseViewModel
import com.hisabak.feature.dashboard.domain.usecase.GetDashboardMetricsUseCase
import com.hisabak.feature.insights.domain.InsightsSummary
import com.hisabak.feature.insights.domain.deriveInsights
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Re-derives the review from the metrics rather than receiving it from the dashboard: the
 * computation is one pure pass, and the alternative — a bus, or a nav key carrying a list — buys
 * nothing for it. [period] arrives from the key, so the screen reviews what the dashboard showed.
 */
class InsightsViewModel(
    private val getMetrics: GetDashboardMetricsUseCase,
    private val analytics: Analytics,
    private val period: SummaryPeriod,
) : BaseViewModel<InsightsIntent, InsightsUiState, InsightsEffect>() {

    private var opened = false

    override fun initialState() = InsightsUiState(period = period)

    init {
        getMetrics(flowOf(period))
            .onEach { snapshot ->
                val insights = deriveInsights(InsightsSummary.from(snapshot, period))
                setState { copy(insights = insights, isLoading = false) }
                if (!opened) {
                    opened = true
                    analytics.log(AnalyticsEvent.InsightsOpened(count = insights.size))
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: InsightsIntent) {
        when (intent) {
            is InsightsIntent.Tapped ->
                analytics.log(AnalyticsEvent.InsightTapped(type = intent.insight.type.name.lowercase()))
        }
    }
}
