package com.hisabak.feature.dashboard.presentation

import com.hisabak.core.common.SummaryPeriod
import com.hisabak.core.presentation.ViewEffect
import com.hisabak.core.presentation.ViewIntent
import com.hisabak.core.presentation.ViewState
import com.hisabak.feature.dashboard.domain.DashboardSnapshot

data class DashboardUiState(
    val snapshot: DashboardSnapshot? = null,
    val isLoading: Boolean = true,
    val period: SummaryPeriod = SummaryPeriod.CURRENT_MONTH,
) : ViewState

sealed interface DashboardIntent : ViewIntent {
    data class PeriodChanged(val period: SummaryPeriod) : DashboardIntent
}

sealed interface DashboardEffect : ViewEffect
