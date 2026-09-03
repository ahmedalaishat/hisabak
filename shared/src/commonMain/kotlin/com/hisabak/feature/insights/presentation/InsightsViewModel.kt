package com.hisabak.feature.insights.presentation

import androidx.lifecycle.viewModelScope
import com.hisabak.core.common.AppConfig
import com.hisabak.core.common.SummaryPeriod
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.core.presentation.BaseViewModel
import com.hisabak.feature.dashboard.domain.usecase.GetDashboardMetricsUseCase
import com.hisabak.feature.insights.domain.InsightsSummary
import com.hisabak.feature.insights.domain.ai.AskInsightUseCase
import com.hisabak.feature.insights.domain.ai.AskResult
import com.hisabak.feature.insights.domain.ai.AskRole
import com.hisabak.feature.insights.domain.ai.AskSource
import com.hisabak.feature.insights.domain.ai.AskTurn
import com.hisabak.feature.insights.domain.ai.GenerateNarrativeUseCase
import com.hisabak.feature.insights.domain.ai.MAX_QUESTION_LENGTH
import com.hisabak.feature.insights.domain.ai.suggestedQuestions
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
 * The narrative (layer 2) is **never fetched on its own**. A snapshot only looks the cache up: an
 * answer already on the phone for exactly these figures is shown straight away — nothing is sent
 * for that — and otherwise the screen asks. The send happens on [InsightsIntent.RequestNarrative]
 * alone, and the answer gives way to the ask again as soon as any figure changes. [language] is the UI language the text is
 * generated in — fixed for the screen's life, since a switch recreates it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModel(
    private val getMetrics: GetDashboardMetricsUseCase,
    private val generateNarrative: GenerateNarrativeUseCase,
    private val askInsight: AskInsightUseCase,
    private val appConfig: AppConfig,
    private val analytics: Analytics,
    private val period: SummaryPeriod,
    private val language: String,
) : BaseViewModel<InsightsIntent, InsightsUiState, InsightsEffect>() {

    private val periodFlow = MutableStateFlow(period)
    private var opened = false
    private var request: Job? = null

    /** The key of the request in flight, so a snapshot for the same figures keeps its Loading state. */
    private var requestKey: String? = null

    override fun initialState() = InsightsUiState(period = period)

    init {
        periodFlow
            .flatMapLatest { p -> getMetrics(flowOf(p)).map { p to it } }
            .onEach { (period, snapshot) ->
                val summary = InsightsSummary.from(snapshot, period)
                val insights = deriveInsights(summary)
                val key = narrativeKey(summary, language)
                val cachedNarrative = if (appConfig.hasParseService) generateNarrative.cached(summary, language) else null
                setState {
                    val narrative = when {
                        !appConfig.hasParseService -> NarrativeUi.Hidden
                        // The saved answer for exactly these figures always wins: it is what this
                        // period was told, whatever was on screen for the previous one.
                        cachedNarrative != null -> NarrativeUi.Ready(cachedNarrative)
                        // Same figures as the request in flight (or its failed outcome): keep it.
                        requestKey == key && (request?.isActive == true || this.narrative is NarrativeUi.Unavailable) -> this.narrative
                        // A fetch in flight for other figures answers a question no longer asked.
                        else -> { request?.cancel(); NarrativeUi.Ask }
                    }
                    copy(
                        period = period,
                        insights = insights,
                        summary = summary,
                        isLoading = false,
                        narrative = narrative,
                        suggestedQuestions = if (appConfig.hasParseService) suggestedQuestions(insights) else emptyList(),
                    )
                }
                if (appConfig.hasParseService && state.value.ask.remaining == null) {
                    val left = askInsight.remaining()
                    setState { copy(ask = ask.copy(remaining = left)) }
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
            is InsightsIntent.OpenAsk -> {
                setState { copy(ask = ask.copy(open = true, notice = null)) }
                intent.question?.let { send(it, AskSource.Chip) }
            }
            InsightsIntent.CloseAsk -> setState { copy(ask = ask.copy(open = false, notice = null)) }
            is InsightsIntent.AskDraftChanged ->
                setState { copy(ask = ask.copy(draft = intent.value.take(MAX_QUESTION_LENGTH))) }
            InsightsIntent.AskSubmitted -> {
                val draft = state.value.ask.draft.trim()
                if (draft.isNotEmpty()) send(draft, AskSource.Free)
            }
        }
    }

    /** The send. Each question is one tap — a chip or Send — and each is counted against today. */
    private fun send(question: String, source: AskSource) {
        val summary = state.value.summary ?: return
        if (state.value.ask.busy) return
        val history = state.value.ask.turns
        setState {
            copy(ask = ask.copy(busy = true, draft = "", notice = null, turns = history + AskTurn(AskRole.User, question)))
        }
        viewModelScope.launch {
            val result = askInsight(summary, language, question, history, source)
            setState {
                val ask = when (result) {
                    is AskResult.Answered -> ask.copy(
                        busy = false,
                        remaining = result.remaining,
                        turns = ask.turns + AskTurn(AskRole.Assistant, result.text),
                    )
                    // The question stays on screen unanswered: the user sees what was asked, and
                    // can retry when the service is back.
                    AskResult.NoQuestionsLeft -> ask.copy(busy = false, remaining = 0, notice = AskNotice.NoQuestionsLeft)
                    AskResult.Unavailable -> ask.copy(busy = false, notice = AskNotice.Unavailable)
                }
                copy(ask = ask)
            }
        }
    }

    private fun requestNarrative() {
        val summary = state.value.summary ?: return
        if (request?.isActive == true) return
        requestKey = narrativeKey(summary, language)
        setState { copy(narrative = NarrativeUi.Loading) }
        request = viewModelScope.launch {
            val result = generateNarrative(summary, language)
            setState { copy(narrative = result.toUi()) }
        }
    }

    private fun NarrativeResult.toUi(): NarrativeUi = when (this) {
        NarrativeResult.Disabled -> NarrativeUi.Hidden
        is NarrativeResult.Ready -> NarrativeUi.Ready(items)
        NarrativeResult.Unavailable -> NarrativeUi.Unavailable
    }
}
