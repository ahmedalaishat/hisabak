package com.hisabak.feature.sms.domain.ai

import com.hisabak.core.common.Clock
import com.hisabak.core.common.Currency
import com.hisabak.core.common.DomainError
import com.hisabak.core.common.DomainResult
import com.hisabak.core.common.Money
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.feature.sms.domain.ParsedSmsData
import com.hisabak.feature.sms.domain.SmsMessage
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.feature.sms.domain.SmsRepository
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Runs the on-device AI parser over a stored, still-unparsed message and stores the result as a
 * **suggestion** (never a transaction — confirm-first, see [ConfirmAiSuggestionUseCase]).
 * [source] is "auto" (capture-time fallback) or "manual" (Parse-with-AI tap).
 */
class SuggestAiParseUseCase(
    private val aiParser: AiSmsParser,
    private val smsRepository: SmsRepository,
    private val defaultCurrency: Currency,
    private val clock: Clock,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(messageId: SmsMessageId, source: String): DomainResult<SmsMessage> {
        val message = when (val result = smsRepository.getById(messageId)) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return result
        }
        if (message.isLinked || message.parsed?.isComplete == true) {
            return DomainResult.Failure(DomainError.Conflict("Message already parsed"))
        }
        if (aiParser.availability() != AiParserAvailability.Ready) {
            analytics.log(AnalyticsEvent.AiParseFailed(source, "unavailable"))
            return DomainResult.Failure(DomainError.ValidationFailed("AI parser unavailable"))
        }

        analytics.log(AnalyticsEvent.AiParseAttempted(source))
        val raw = aiParser.parse(message.body)
        if (raw == null) {
            analytics.log(AnalyticsEvent.AiParseFailed(source, "model_empty"))
            return DomainResult.Failure(DomainError.ValidationFailed("AI parse produced nothing"))
        }
        val suggestion = sanitize(raw, message.receivedAt)
        if (suggestion == null) {
            analytics.log(AnalyticsEvent.AiParseFailed(source, "incomplete"))
            return DomainResult.Failure(DomainError.ValidationFailed("AI parse incomplete"))
        }

        analytics.log(AnalyticsEvent.AiParseSucceeded(source, suggestion.amount!!))
        val updated = message.copy(suggested = suggestion)
        return smsRepository.upsert(updated).map { updated }
    }

    /**
     * Common guardrails regardless of which platform model produced the output: brand and a
     * positive amount are required, the currency defaults to the app currency, and a missing or
     * implausible date (model hallucinations land in the far future) falls back to when the SMS
     * arrived — the same rule the template pipeline applies.
     */
    private fun sanitize(raw: AiParsedSms, receivedAt: Instant): ParsedSmsData? {
        val brand = raw.brandName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val amountMinor = raw.amountMinor?.takeIf { it > 0 } ?: return null
        val currencyCode = raw.currencyCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()
            ?: defaultCurrency.code
        val occurredAt = raw.occurredAtEpochMillis
            ?.let(Instant::fromEpochMilliseconds)
            ?.takeIf { it <= clock.now() + 1.days }
            ?: receivedAt
        return ParsedSmsData(
            brandName = brand,
            amount = Money(amountMinor, Currency(currencyCode)),
            occurredAt = occurredAt,
        )
    }
}
