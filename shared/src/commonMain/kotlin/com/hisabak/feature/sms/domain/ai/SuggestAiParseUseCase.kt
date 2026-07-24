package com.hisabak.feature.sms.domain.ai

import com.hisabak.core.common.Clock
import com.hisabak.core.common.Currency
import com.hisabak.core.common.DomainError
import com.hisabak.core.common.DomainResult
import com.hisabak.core.common.Money
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.feature.brand.domain.BrandRepository
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
    private val brandRepository: BrandRepository,
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
        val knownBrands = brandRepository.namesByUsage(MAX_KNOWN_BRANDS)
        val raw = aiParser.parse(message.body, knownBrands)
        if (raw == null) {
            analytics.log(AnalyticsEvent.AiParseFailed(source, "model_empty"))
            return DomainResult.Failure(DomainError.ValidationFailed("AI parse produced nothing"))
        }
        val suggestion = sanitize(raw, message.receivedAt, knownBrands)
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
     * positive amount are required, and the currency defaults to the app currency. A model date
     * is trusted only inside a plausibility window around the SMS arrival — dateless messages
     * provoke hallucinated dates months in the past (observed on-device: "400 lulu" → Oct 2025),
     * and the far future is equally fictional — anything outside falls back to when the SMS
     * arrived, the same rule the template pipeline applies.
     */
    private fun sanitize(raw: AiParsedSms, receivedAt: Instant, knownBrands: List<String>): ParsedSmsData? {
        val brand = raw.brandName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val amountMinor = raw.amountMinor?.takeIf { it > 0 } ?: return null
        val currencyCode = raw.currencyCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()
            ?: defaultCurrency.code
        val occurredAt = raw.occurredAtEpochMillis
            ?.let(Instant::fromEpochMilliseconds)
            ?.takeIf { it >= receivedAt - MAX_DATE_AGE && it <= clock.now() + 1.days }
            ?: receivedAt
        return ParsedSmsData(
            brandName = canonicalize(brand, knownBrands),
            amount = Money(amountMinor, Currency(currencyCode)),
            occurredAt = occurredAt,
        )
    }

    /**
     * Deterministic backstop under the prompt-side brand hints: snap the model's merchant string
     * to an existing brand name so the suggestion shows exactly what Confirm will link. Match
     * order — case-insensitive exact, then substring either way (the same containment rule
     * `FindOrCreateBrandUseCase.findByNameLike` applies at link time), then a small edit
     * distance for typos. [knownBrands] is usage-ordered, so ties go to the most-used brand.
     */
    private fun canonicalize(raw: String, knownBrands: List<String>): String {
        knownBrands.firstOrNull { it.equals(raw, ignoreCase = true) }?.let { return it }
        knownBrands.firstOrNull {
            raw.contains(it, ignoreCase = true) || it.contains(raw, ignoreCase = true)
        }?.let { return it }
        if (raw.length >= MIN_TYPO_LENGTH) {
            knownBrands.firstOrNull {
                it.length >= MIN_TYPO_LENGTH &&
                    levenshtein(raw.lowercase(), it.lowercase()) <= MAX_TYPO_DISTANCE
            }?.let { return it }
        }
        return raw
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        var previous = IntArray(b.length + 1) { it }
        val current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, substitution)
            }
            previous = current.copyInto(IntArray(b.length + 1))
        }
        return previous[b.length]
    }

    private companion object {
        const val MAX_KNOWN_BRANDS = 50
        const val MIN_TYPO_LENGTH = 4
        const val MAX_TYPO_DISTANCE = 2

        /** How far before the SMS arrival a model-claimed date is still believable. Bank alerts
         *  arrive near the transaction; a pasted backlog message older than this gets dated at
         *  paste time and can be corrected in the transaction editor. */
        val MAX_DATE_AGE = 7.days
    }
}
