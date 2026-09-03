package com.hisabak.feature.insights.presentation

import com.hisabak.core.common.SummaryPeriod
import com.hisabak.core.presentation.ViewEffect
import com.hisabak.core.presentation.ViewIntent
import com.hisabak.core.presentation.ViewState
import com.hisabak.feature.insights.domain.Insight
import com.hisabak.feature.insights.domain.InsightsSummary
import com.hisabak.feature.insights.domain.ai.NarrativeInsight

/** The AI layer's place on the screen. The deterministic [InsightsUiState.insights] never depends on it. */
sealed interface NarrativeUi {
    /** No service in this build: the screen shows no AI at all. */
    data object Hidden : NarrativeUi

    /** A service exists; every visit starts here — nothing is shown, let alone sent, before the tap. */
    data object Ask : NarrativeUi

    data object Loading : NarrativeUi

    data class Ready(val items: List<NarrativeInsight>) : NarrativeUi

    /** The service did not answer. [stale] is the previous narrative for this period, if any. */
    data class Unavailable(val stale: List<NarrativeInsight>?) : NarrativeUi
}

data class InsightsUiState(
    val period: SummaryPeriod,
    val insights: List<Insight> = emptyList(),
    val isLoading: Boolean = true,
    /** What "See what's shared" shows — the exact payload, so the promise is checkable on screen. */
    val summary: InsightsSummary? = null,
    val narrative: NarrativeUi = NarrativeUi.Hidden,
    val showShared: Boolean = false,
) : ViewState

sealed interface InsightsIntent : ViewIntent {
    /** Analytics only — navigation is the Route's job. */
    data class Tapped(val insight: Insight) : InsightsIntent

    data class NarrativeTapped(val item: NarrativeInsight) : InsightsIntent

    /** Analytics only — the Route opens the category editor with the proposed cap. */
    data class SuggestionAccepted(val item: NarrativeInsight) : InsightsIntent

    /** "Explain with AI": the one action that sends the summary. Consent is this tap. */
    data object RequestNarrative : InsightsIntent

    data object ShowShared : InsightsIntent

    data object HideShared : InsightsIntent
}

sealed interface InsightsEffect : ViewEffect
