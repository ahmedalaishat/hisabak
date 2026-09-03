package com.hisabak.feature.insights.presentation

import androidx.lifecycle.viewModelScope
import com.hisabak.core.common.AppConfig
import com.hisabak.core.common.SummaryPeriod
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.core.presentation.BaseViewModel
import com.hisabak.feature.dashboard.domain.usecase.GetDashboardMetricsUseCase
import com.hisabak.feature.insights.domain.InsightsSummary
import com.hisabak.feature.insights.domain.ai.GenerateNarrativeUseCase
import com.hisabak.feature.insights.domain.ai.NarrativeResult
import com.hisabak.feature.insights.domain.ai.narrativeKey
import com.hisabak.feature.insights.domain.deriveInsights
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Re-derives the review from the metrics rather than receiving it from the dashboard: the
 * computation is one pure pass, and the alternative — a bus, or a nav key carrying a list — buys
 * nothing for it. [period] arrives from the key, so the screen opens on what the dashboard showed,
 * and the period chips on the screen re-scope it from there.
 *
 * The narrative (layer 2) appears **only after the user's tap** — every visit starts at the ask,
 * even when the cache already holds an answer for these figures (the tap is then answered from
 * the cache without a send). Within a visit the answer stays while the figures are unchanged and
 * gives way to the ask again when they materially change. The send happens on
 * [InsightsIntent.RequestNarrative] alone. [language] is the UI language the text is
 * generated in — fixed for the screen's life, since a switch recreates it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModel(
    private val getMetrics: GetDashboardMetricsUseCase,
    private val generateNarrative: GenerateNarrativeUseCase,
    private val appConfig: AppConfig,
    private val analytics: Analytics,
    private val period: SummaryPeriod,
    private val language: String,
) : BaseViewModel<InsightsIntent, InsightsUiState, InsightsEffect>() {

    private val periodFlow = MutableStateFlow(period)
    private var opened = false
    private var request: Job? = null

    /** The key the shown answer was produced for; a snapshot with another key asks again. */
    private var answeredKey: String? = null

    override fun initialState() = InsightsUiState(period = period)

    init {
        periodFlow
            .flatMapLatest { p -> getMetrics(flowOf(p)).map { p to it } }
            .onEach { (period, snapshot) ->
                val summary = InsightsSummary.from(snapshot, period)
                val insights = deriveInsights(summary)
                val key = narrativeKey(summary, language)
                setState {
                    val narrative = when {
                        !appConfig.hasParseService -> NarrativeUi.Hidden
                        // A fetch in flight for the previous figures answers a question that is no
                        // longer being asked.
                        request?.isActive == true -> { request?.cancel(); NarrativeUi.Ask }
                        this.narrative is NarrativeUi.Ready && answeredKey == key -> this.narrative
                        else -> NarrativeUi.Ask
                    }
                    copy(period = period, insights = insights, summary = summary, isLoading = false, narrative = narrative)
                }
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
            is InsightsIntent.NarrativeTapped ->
                analytics.log(AnalyticsEvent.InsightTapped(type = "narrative"))
            is InsightsIntent.SuggestionAccepted ->
                analytics.log(AnalyticsEvent.InsightsSuggestionAccepted(type = "set_limit"))
            is InsightsIntent.PeriodChanged -> periodFlow.value = intent.period
            InsightsIntent.RequestNarrative -> requestNarrative()
            InsightsIntent.ShowShared -> setState { copy(showShared = true) }
            InsightsIntent.HideShared -> setState { copy(showShared = false) }
        }
    }

    private fun requestNarrative() {
        val summary = state.value.summary ?: return
        if (request?.isActive == true) return
        setState { copy(narrative = NarrativeUi.Loading) }
        request = viewModelScope.launch {
            val result = generateNarrative(summary, language)
            answeredKey = narrativeKey(summary, language)
            setState { copy(narrative = result.toUi()) }
        }
    }

    private fun NarrativeResult.toUi(): NarrativeUi = when (this) {
        NarrativeResult.Disabled -> NarrativeUi.Hidden
        is NarrativeResult.Ready -> NarrativeUi.Ready(items)
        is NarrativeResult.Unavailable -> NarrativeUi.Unavailable(stale)
    }
}
