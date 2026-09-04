package com.hisabak.feature.insights.presentation

import com.hisabak.core.common.SummaryPeriod
import com.hisabak.core.presentation.ViewEffect
import com.hisabak.core.presentation.ViewIntent
import com.hisabak.core.presentation.ViewState
import com.hisabak.feature.insights.domain.Insight
import com.hisabak.feature.insights.domain.InsightsSummary
import com.hisabak.feature.insights.domain.ai.NarrativeInsight
import com.hisabak.feature.insights.domain.ai.SuggestedQuestion

/** The AI layer's place on the screen. The deterministic [InsightsUiState.insights] never depends on it. */
sealed interface NarrativeUi {
    /** No service in this build: the screen shows no AI at all. */
    data object Hidden : NarrativeUi

    /** A service exists and nothing is saved for exactly these figures — the send is one tap away. */
    data object Ask : NarrativeUi

    data object Loading : NarrativeUi

    data class Ready(val items: List<NarrativeInsight>) : NarrativeUi

    /** The service did not answer; the findings below stand on their own. */
    data object Unavailable : NarrativeUi
}

data class InsightsUiState(
    val period: SummaryPeriod,
    val insights: List<Insight> = emptyList(),
    val isLoading: Boolean = true,
    /** What "See what's shared" shows — the exact payload, so the promise is checkable on screen. */
    val summary: InsightsSummary? = null,
    val narrative: NarrativeUi = NarrativeUi.Hidden,
    val showShared: Boolean = false,
    /** False when the build has no service: there is nothing in the assistant tab, so it is hidden. */
    val showAiTab: Boolean = false,
    /** Questions derived from the findings; empty when the build has no service. */
    val suggestedQuestions: List<SuggestedQuestion> = emptyList(),
    /** Questions left today, for the entry card; null until read. */
    val askRemaining: Int? = null,
) : ViewState

sealed interface InsightsIntent : ViewIntent {
    /** The period chips live on this screen too, so the review can be re-scoped without going back. */
    data class PeriodChanged(val period: SummaryPeriod) : InsightsIntent

    /** Analytics only — navigation is the Route's job. */
    data class Tapped(val insight: Insight) : InsightsIntent

    data class NarrativeTapped(val item: NarrativeInsight) : InsightsIntent

    /** Analytics only — the Route opens the category editor with the proposed cap. */
    data class SuggestionAccepted(val item: NarrativeInsight) : InsightsIntent

    /** "Explain with AI": the one action that sends the summary. Consent is this tap. */
    data object RequestNarrative : InsightsIntent

    data object ShowShared : InsightsIntent

    data object HideShared : InsightsIntent

    /** Analytics only — the Route pushes the Ask screen, with [question] when a suggestion was tapped. */
    data class AskOpened(val question: String?) : InsightsIntent
}

sealed interface InsightsEffect : ViewEffect
