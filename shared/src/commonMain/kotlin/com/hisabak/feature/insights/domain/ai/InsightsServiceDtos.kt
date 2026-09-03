package com.hisabak.feature.insights.domain.ai

import com.hisabak.core.common.Currency
import com.hisabak.feature.insights.domain.InsightsSummary
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire types for `/v1/insights`, snake_case to match the Python schema.
 *
 * This is the exact payload that leaves the phone, and its field list is the privacy promise: totals
 * by category with prior-period figures and limits, the period, the currency, the language. No
 * transaction, no note, no brand, no message text — there is no field to put one in.
 */
@Serializable
data class InsightsRequestDto(
    val period: String,
    val currency: String,
    val language: String,
    @SerialName("income_minor") val incomeMinor: Long,
    @SerialName("expense_minor") val expenseMinor: Long,
    @SerialName("prior_income_minor") val priorIncomeMinor: Long?,
    @SerialName("prior_expense_minor") val priorExpenseMinor: Long?,
    val categories: List<CategoryFiguresDto>,
    @SerialName("uncategorized_minor") val uncategorizedMinor: Long,
    @SerialName("uncategorized_count") val uncategorizedCount: Int,
)

@Serializable
data class CategoryFiguresDto(
    val id: String,
    val name: String,
    @SerialName("spent_minor") val spentMinor: Long,
    @SerialName("prior_minor") val priorMinor: Long?,
    @SerialName("limit_minor") val limitMinor: Long?,
)

@Serializable
data class InsightsResponseDto(
    val items: List<NarrativeItemDto> = emptyList(),
)

@Serializable
data class NarrativeItemDto(
    @SerialName("category_id") val categoryId: String? = null,
    val headline: String = "",
    val detail: String = "",
    @SerialName("suggested_limit_minor") val suggestedLimitMinor: Long? = null,
) {
    /** Straight mapping — [sanitizeNarrative] owns acceptance, so the transport applies no rules. */
    fun toDomain() = RawNarrativeInsight(categoryId, headline, detail, suggestedLimitMinor)
}

/** Mirrors the server's `MAX_CATEGORIES`; a ledger with more sends its largest. */
const val MAX_REQUEST_CATEGORIES = 60

/** Mirrors the server's `CategoryName` bound; a longer name is cut, not rejected. */
const val MAX_REQUEST_NAME_LENGTH = 60

fun InsightsSummary.toRequestDto(language: String, currency: Currency) = InsightsRequestDto(
    period = period.name,
    currency = currency.code,
    language = language,
    incomeMinor = incomeMinor,
    expenseMinor = expenseMinor,
    priorIncomeMinor = priorIncomeMinor,
    priorExpenseMinor = priorExpenseMinor,
    // Largest first, then the summary's own (id) order for ties, so the payload is byte-stable for
    // a given ledger and the cut — if there is one — drops the immaterial tail.
    categories = categories
        .sortedWith(compareByDescending<com.hisabak.feature.insights.domain.CategorySpend> { it.spentMinor }.thenBy { it.id.value })
        .take(MAX_REQUEST_CATEGORIES)
        .map {
            CategoryFiguresDto(
                id = it.id.value,
                name = it.name.take(MAX_REQUEST_NAME_LENGTH),
                spentMinor = it.spentMinor,
                priorMinor = it.priorMinor,
                limitMinor = it.limitMinor,
            )
        },
    uncategorizedMinor = uncategorizedMinor,
    uncategorizedCount = uncategorizedCount,
)

// ── Ask ───────────────────────────────────────────────────────────────────────

@Serializable
data class AskTurnDto(val role: String, val text: String)

/** The question rides with the same summary the narrative uses — that summary is the whole context. */
@Serializable
data class AskRequestDto(
    val summary: InsightsRequestDto,
    val question: String,
    val history: List<AskTurnDto>,
)

@Serializable
data class AskResponseDto(
    val answer: String = "",
    @SerialName("on_topic") val onTopic: Boolean = true,
)
