package com.hisabak.feature.insights.presentation.ask

import com.hisabak.core.common.SummaryPeriod
import com.hisabak.core.presentation.ViewEffect
import com.hisabak.core.presentation.ViewIntent
import com.hisabak.core.presentation.ViewState
import com.hisabak.feature.insights.domain.ai.AskTurn
import com.hisabak.feature.insights.domain.ai.SuggestedQuestion

/** Why the composer is refusing. Both are recoverable, so neither is an error screen. */
enum class AskNotice { NoQuestionsLeft, Unavailable }

/**
 * The conversation lives here and nowhere else: leaving the screen ends it. Nothing is persisted,
 * which is the point — a question about your finances should not outlive the asking.
 */
data class AskUiState(
    val period: SummaryPeriod,
    val turns: List<AskTurn> = emptyList(),
    val suggestions: List<SuggestedQuestion> = emptyList(),
    val draft: String = "",
    val busy: Boolean = false,
    /** Questions left today; null until the allowance has been read. */
    val remaining: Int? = null,
    val notice: AskNotice? = null,
) : ViewState {
    val isEmpty: Boolean get() = turns.isEmpty()
    val canSend: Boolean get() = draft.isNotBlank() && !busy && (remaining ?: 1) > 0
}

sealed interface AskIntent : ViewIntent {
    data class DraftChanged(val value: String) : AskIntent

    /** Sends the draft. Consent is this tap. */
    data object Submitted : AskIntent

    /** Sends a suggested question verbatim — what the user read is what the model is asked. */
    data class QuestionTapped(val text: String) : AskIntent

    data object NoticeDismissed : AskIntent
}

sealed interface AskEffect : ViewEffect
