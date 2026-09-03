package com.hisabak.feature.insights.presentation

import androidx.lifecycle.viewModelScope
import com.hisabak.core.common.AppConfig
import com.hisabak.core.common.SummaryPeriod
import com.hisabak.core.domain.AppPreferences
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.core.presentation.BaseViewModel
import com.hisabak.feature.dashboard.domain.usecase.GetDashboardMetricsUseCase
import com.hisabak.feature.insights.domain.InsightsSummary
import com.hisabak.feature.insights.domain.ai.GenerateNarrativeUseCase
import com.hisabak.feature.insights.domain.ai.NarrativeResult
import com.hisabak.feature.insights.domain.deriveInsights
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch

/**
 * Re-derives the review from the metrics rather than receiving it from the dashboard: the
 * computation is one pure pass, and the alternative — a bus, or a nav key carrying a list — buys
 * nothing for it. [period] arrives from the key, so the screen reviews what the dashboard showed.
 *
 * The narrative (layer 2) rides the same stream: every snapshot re-derives the review at once and
 * then asks [GenerateNarrativeUseCase], whose cache decides whether that costs a call. `mapLatest`
 * drops an in-flight generation when the ledger changes underneath it. [language] is the UI
 * language the text is generated in — fixed for the screen's life, since a switch recreates it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModel(
    private val getMetrics: GetDashboardMetricsUseCase,
    private val generateNarrative: GenerateNarrativeUseCase,
    private val preferences: AppPreferences,
    private val appConfig: AppConfig,
    private val analytics: Analytics,
    private val period: SummaryPeriod,
    private val language: String,
) : BaseViewModel<InsightsIntent, InsightsUiState, InsightsEffect>() {

    private var opened = false

    /** "Not now" lives here, not in prefs: the user said not now, not never, and re-entering the
     *  screen (which scopes a fresh ViewModel) is the natural moment to ask once more. */
    private var offerDismissed = false

    override fun initialState() = InsightsUiState(period = period)

    init {
        combine(getMetrics(flowOf(period)), preferences.insightsEnabled) { snapshot, enabled -> snapshot to enabled }
            .mapLatest { (snapshot, enabled) ->
                val summary = InsightsSummary.from(snapshot, period)
                val insights = deriveInsights(summary)
                val narrativeOn = enabled && appConfig.hasParseService
                setState {
                    copy(
                        insights = insights,
                        summary = summary,
                        isLoading = false,
                        narrative = when {
                            !appConfig.hasParseService -> NarrativeUi.Hidden
                            !enabled -> if (offerDismissed) NarrativeUi.Hidden else NarrativeUi.Offer
                            // Keep the last narrative on screen while a fresh one is fetched.
                            narrative is NarrativeUi.Ready -> narrative
                            else -> NarrativeUi.Loading
                        },
                    )
                }
                if (!opened) {
                    opened = true
                    analytics.log(AnalyticsEvent.InsightsOpened(count = insights.size))
                }
                if (narrativeOn) {
                    val result = generateNarrative(summary, language)
                    setState { copy(narrative = result.toUi()) }
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: InsightsIntent) {
        when (intent) {
            is InsightsIntent.Tapped ->
                analytics.log(AnalyticsEvent.InsightTapped(type = intent.insight.type.name.lowercase()))
            is InsightsIntent.NarrativeTapped ->
                analytics.log(AnalyticsEvent.InsightTapped(type = "narrative"))
            is InsightsIntent.SuggestionAccepted ->
                analytics.log(AnalyticsEvent.InsightsSuggestionAccepted(type = "set_limit"))
            InsightsIntent.EnableNarrative -> {
                analytics.log(AnalyticsEvent.InsightsToggled(enabled = true))
                viewModelScope.launch { preferences.setInsightsEnabled(true) }
            }
            InsightsIntent.DismissOffer -> {
                offerDismissed = true
                setState { copy(narrative = NarrativeUi.Hidden) }
            }
            InsightsIntent.ShowShared -> setState { copy(showShared = true) }
            InsightsIntent.HideShared -> setState { copy(showShared = false) }
        }
    }

    private fun NarrativeResult.toUi(): NarrativeUi = when (this) {
        NarrativeResult.Disabled -> NarrativeUi.Offer
        is NarrativeResult.Ready -> NarrativeUi.Ready(items)
        is NarrativeResult.Unavailable -> NarrativeUi.Unavailable(stale)
    }
}
