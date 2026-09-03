package com.hisabak.feature.insights.domain.ai

import com.hisabak.core.common.Clock
import com.hisabak.core.common.Currency
import com.hisabak.core.domain.AppPreferences
import com.hisabak.core.domain.AskTally
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.feature.insights.domain.InsightsSummary
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.first

/**
 * Layer 3: one question about the summary, answered by the service.
 *
 * Consent is the question itself — nothing is sent until the user taps a chip or Send. The visible
 * allowance ([ASK_DAILY_ALLOWANCE] a day) is counted here, on the phone, so the screen can say
 * "3 of 10 left" and refuse locally before a round trip; the server keeps its own count under the
 * same install id and is the one that actually holds. The install id is a random UUID minted once
 * and tied to nothing — fairness, not identity.
 */
class AskInsightUseCase(
    private val client: RemoteInsightsClient,
    private val preferences: AppPreferences,
    private val currency: Currency,
    private val clock: Clock,
    private val analytics: Analytics,
) {
    fun isAvailable(): Boolean = client.isConfigured

    /** Questions left today; the tally resets with the local date. */
    suspend fun remaining(): Int {
        val tally = preferences.askTally.first()
        val today = clock.today().toString()
        return if (tally.day == today) (ASK_DAILY_ALLOWANCE - tally.count).coerceAtLeast(0) else ASK_DAILY_ALLOWANCE
    }

    suspend operator fun invoke(
        summary: InsightsSummary,
        language: String,
        question: String,
        history: List<AskTurn>,
        source: AskSource,
    ): AskResult {
        val trimmed = question.trim().take(MAX_QUESTION_LENGTH)
        if (trimmed.isEmpty() || !isAvailable()) return AskResult.Unavailable
        val left = remaining()
        if (left <= 0) {
            analytics.log(AnalyticsEvent.InsightsQuotaHit(scope = "install_daily"))
            return AskResult.NoQuestionsLeft
        }

        val request = AskRequestDto(
            summary = summary.toRequestDto(language, currency),
            question = trimmed,
            history = history.takeLast(ASK_HISTORY_TURNS).map {
                AskTurnDto(role = if (it.role == AskRole.User) "user" else "assistant", text = it.text)
            },
        )
        val response = client.ask(request, installId()) ?: return AskResult.Unavailable

        val today = clock.today().toString()
        val tally = preferences.askTally.first()
        preferences.setAskTally(AskTally(day = today, count = if (tally.day == today) tally.count + 1 else 1))
        analytics.log(AnalyticsEvent.InsightsAsk(source = source.name.lowercase(), onTopic = response.onTopic))
        return AskResult.Answered(text = response.answer.trim(), onTopic = response.onTopic, remaining = left - 1)
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun installId(): String =
        preferences.installId.first() ?: Uuid.random().toString().also { preferences.setInstallId(it) }
}
