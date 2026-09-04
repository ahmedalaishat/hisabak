package com.hisabak.feature.insights.presentation.ask

import androidx.lifecycle.viewModelScope
import com.hisabak.core.common.SummaryPeriod
import com.hisabak.core.presentation.BaseViewModel
import com.hisabak.feature.dashboard.domain.usecase.GetDashboardMetricsUseCase
import com.hisabak.feature.insights.domain.InsightsSummary
import com.hisabak.feature.insights.domain.ai.AskInsightUseCase
import com.hisabak.feature.insights.domain.ai.AskResult
import com.hisabak.feature.insights.domain.ai.AskRole
import com.hisabak.feature.insights.domain.ai.AskSource
import com.hisabak.feature.insights.domain.ai.AskTurn
import com.hisabak.feature.insights.domain.ai.MAX_QUESTION_LENGTH
import com.hisabak.feature.insights.domain.ai.suggestedQuestions
import com.hisabak.feature.insights.domain.deriveInsights
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * The Ask screen. It rebuilds the summary from the metrics for [period] rather than receiving it,
 * for the same reason the review does: the computation is one pure pass, and a bus or a fat nav key
 * would buy nothing. That also keeps the question answered against figures that are current at the
 * moment it is asked.
 *
 * [initialQuestion] is the chip the user tapped to get here, sent once on arrival — the tap on the
 * insights screen was the send, so the screen must not wait for a second one.
 */
class AskViewModel(
    private val getMetrics: GetDashboardMetricsUseCase,
    private val askInsight: AskInsightUseCase,
    private val period: SummaryPeriod,
    private val language: String,
    private val initialQuestion: String?,
) : BaseViewModel<AskIntent, AskUiState, AskEffect>() {

    private var summary: InsightsSummary? = null
    private var sentInitial = false

    override fun initialState() = AskUiState(period = period)

    init {
        getMetrics(flowOf(period))
            .onEach { snapshot ->
                val built = InsightsSummary.from(snapshot, period)
                summary = built
                setState { copy(suggestions = suggestedQuestions(deriveInsights(built))) }
                if (state.value.remaining == null) {
                    val left = askInsight.remaining()
                    setState { copy(remaining = left) }
                }
                if (!sentInitial) {
                    sentInitial = true
                    initialQuestion?.takeIf { it.isNotBlank() }?.let { send(it, AskSource.Chip) }
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: AskIntent) {
        when (intent) {
            is AskIntent.DraftChanged -> setState { copy(draft = intent.value.take(MAX_QUESTION_LENGTH)) }
            AskIntent.Submitted -> state.value.draft.trim().takeIf { it.isNotEmpty() }?.let { send(it, AskSource.Free) }
            is AskIntent.QuestionTapped -> send(intent.text, AskSource.Chip)
            AskIntent.NoticeDismissed -> setState { copy(notice = null) }
        }
    }

    private fun send(question: String, source: AskSource) {
        val current = summary ?: return
        if (state.value.busy) return
        val history = state.value.turns
        setState { copy(busy = true, draft = "", notice = null, turns = history + AskTurn(AskRole.User, question)) }
        viewModelScope.launch {
            when (val result = askInsight(current, language, question, history, source)) {
                is AskResult.Answered -> setState {
                    copy(busy = false, remaining = result.remaining, turns = turns + AskTurn(AskRole.Assistant, result.text))
                }
                // The question stays on screen unanswered: the user sees what was asked and can
                // retry, and neither outcome spent the allowance.
                AskResult.NoQuestionsLeft -> setState { copy(busy = false, remaining = 0, notice = AskNotice.NoQuestionsLeft) }
                AskResult.Unavailable -> setState { copy(busy = false, notice = AskNotice.Unavailable) }
            }
        }
    }
}
