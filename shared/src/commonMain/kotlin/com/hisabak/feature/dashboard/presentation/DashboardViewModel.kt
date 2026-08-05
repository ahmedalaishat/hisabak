package com.hisabak.feature.dashboard.presentation

import androidx.lifecycle.viewModelScope
import com.hisabak.core.common.SummaryPeriod
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.core.presentation.BaseViewModel
import com.hisabak.feature.dashboard.domain.usecase.GetDashboardMetricsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class DashboardViewModel(
    private val getMetrics: GetDashboardMetricsUseCase,
    private val analytics: Analytics,
) : BaseViewModel<DashboardIntent, DashboardUiState, DashboardEffect>() {

    private val period = MutableStateFlow(SummaryPeriod.CURRENT_MONTH)

    override fun initialState() = DashboardUiState()

    init {
        getMetrics(period)
            .onEach { snapshot -> setState { copy(snapshot = snapshot, isLoading = false) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: DashboardIntent) {
        when (intent) {
            is DashboardIntent.PeriodChanged -> {
                period.value = intent.period
                setState { copy(period = intent.period) }
                analytics.log(AnalyticsEvent.DashboardPeriodChanged(period = intent.period.name.lowercase()))
            }
        }
    }
}
