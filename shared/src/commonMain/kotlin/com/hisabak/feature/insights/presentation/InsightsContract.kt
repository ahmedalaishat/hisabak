package com.hisabak.feature.insights.presentation

import com.hisabak.core.common.SummaryPeriod
import com.hisabak.core.presentation.ViewEffect
import com.hisabak.core.presentation.ViewIntent
import com.hisabak.core.presentation.ViewState
import com.hisabak.feature.insights.domain.Insight

data class InsightsUiState(
    val period: SummaryPeriod,
    val insights: List<Insight> = emptyList(),
    val isLoading: Boolean = true,
) : ViewState

sealed interface InsightsIntent : ViewIntent {
    /** Analytics only — navigation is the Route's job. */
    data class Tapped(val insight: Insight) : InsightsIntent
}

sealed interface InsightsEffect : ViewEffect
