package com.hisabak.feature.insights.domain.ai

import com.hisabak.feature.insights.domain.InsightCategory

enum class AskRole { User, Assistant }

/** One turn of the conversation on the Ask sheet. Kept in memory for the sheet's life only. */
data class AskTurn(val role: AskRole, val text: String)

/** Where a question came from — the measurement behind whether the free-text box earns its cost. */
enum class AskSource { Chip, Free }

sealed interface AskResult {
    /** [remaining] is what is left of today's allowance after this question. */
    data class Answered(val text: String, val onTopic: Boolean, val remaining: Int) : AskResult

    data object NoQuestionsLeft : AskResult

    /** The service could not answer (offline, an outage, or its own limit). Nothing was counted. */
    data object Unavailable : AskResult
}

/** A question the screen can offer as a chip. Carries figures, never copy — the screen renders it. */
data class SuggestedQuestion(val kind: Kind, val category: InsightCategory? = null) {
    enum class Kind { WhyUp, StayUnderLimit, WhereToCut, ImproveSavings, Uncategorized, FocusOn }

    val id: String get() = "${kind}:${category?.id?.value ?: "-"}"
}

/** The one free-text field: mirrors the server's `MAX_QUESTION`. */
const val MAX_QUESTION_LENGTH = 500

/** How many turns ride with a question, mirroring the server's `MAX_HISTORY_TURNS`. */
const val ASK_HISTORY_TURNS = 6

/** The visible daily allowance, mirroring the server's default `HISABAK_ASK_PER_INSTALL_DAILY`. */
const val ASK_DAILY_ALLOWANCE = 10
