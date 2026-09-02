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
import com.hisabak.feature.sms.domain.template.deriveAiSpans
import com.hisabak.feature.sms.domain.template.deriveTemplatePattern
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/**
 * Runs the on-device AI parser over a stored, still-unparsed message and stores the result as a
 * **suggestion** (never a transaction — confirm-first, see [ConfirmAiSuggestionUseCase]).
 * [source] is "auto" (capture-time fallback), "manual" (Parse-with-AI tap), or "paste"
 * (unmatched inbox input, driven by the ViewModel with [freeText] = true).
 */
class SuggestAiParseUseCase(
    private val aiParser: AiSmsParser,
    private val smsRepository: SmsRepository,
    private val brandRepository: BrandRepository,
    private val defaultCurrency: Currency,
    private val clock: Clock,
    private val analytics: Analytics,
) {
    /**
     * [freeText] switches to the note-oriented prompt (today's-date context, so "yesterday"
     * resolves; shorthand like "15k" expands) and widens the date window to a year back —
     * pasted input is legitimately a backlog, unlike a live bank alert.
     */
    suspend operator fun invoke(
        messageId: SmsMessageId,
        source: String,
        freeText: Boolean = false,
    ): DomainResult<SmsMessage> {
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
        val raw = if (freeText) {
            aiParser.parseFreeText(message.body, knownBrands, todayContext())
        } else {
            aiParser.parse(message.body, knownBrands)
        }
        if (raw == null) {
            analytics.log(AnalyticsEvent.AiParseFailed(source, "model_empty"))
            return DomainResult.Failure(DomainError.ValidationFailed("AI parse produced nothing"))
        }
        val suggestion = sanitize(raw, message.receivedAt, knownBrands, maxDateAge = if (freeText) MAX_DATE_AGE_FREE_TEXT else MAX_DATE_AGE)
        if (suggestion == null) {
            analytics.log(AnalyticsEvent.AiParseFailed(source, "incomplete"))
            return DomainResult.Failure(DomainError.ValidationFailed("AI parse incomplete"))
        }

        analytics.log(AnalyticsEvent.AiParseSucceeded(source, suggestion.amount!!))
        // Derived here rather than on confirm because it needs the model's *raw* merchant string:
        // sanitize canonicalizes the brand to an existing one, which often isn't the text the
        // message actually contains.
        //
        // Derived for free text too: the inbox takes pasted bank SMS and typed notes through the
        // same field, so the source can't say which this is — and on iOS, where there is no SMS
        // API, pasting is how bank messages arrive. Whether it earns a template is
        // SynthesizeTemplateUseCase's call; a note like "lunch 45 yesterday" leaves too little
        // literal anchor to pass its gate.
        val candidatePattern = derivePattern(message.body, raw, suggestion)
        val updated = message.copy(suggested = suggestion, suggestedPattern = candidatePattern)
        return smsRepository.upsert(updated).map { updated }
    }

    /**
     * Common guardrails regardless of which platform model produced the output: brand and a
     * positive amount are required, and the currency must look like an ISO-4217 code — models
     * emit local abbreviations and symbols ("Dhs", "د.إ") that would abort [Currency]'s
     * validation, so anything else falls back to the app currency. A model date
     * is trusted only inside a plausibility window around the SMS arrival — dateless messages
     * provoke hallucinated dates months in the past (observed on-device: "400 lulu" → Oct 2025),
     * and the far future is equally fictional — anything outside falls back to when the SMS
     * arrived, the same rule the template pipeline applies.
     */
    private fun sanitize(
        raw: AiParsedSms,
        receivedAt: Instant,
        knownBrands: List<String>,
        maxDateAge: kotlin.time.Duration,
    ): ParsedSmsData? {
        val brand = raw.brandName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        // Ceiling as well as floor: the model controls this value, and an absurd magnitude
        // would flow through confirm into Money sums that can overflow Long.
        val amountMinor = raw.amountMinor?.takeIf { it in 1..MAX_AMOUNT_MINOR } ?: return null
        val currencyCode = raw.currencyCode?.trim()?.uppercase()
            ?.takeIf { code -> code.length == 3 && code.all { it in 'A'..'Z' } }
            ?: defaultCurrency.code
        val occurredAt = raw.occurredAtEpochMillis
            ?.let(Instant::fromEpochMilliseconds)
            ?.takeIf { it >= receivedAt - maxDateAge && it <= clock.now() + 1.days }
            ?: receivedAt
        return ParsedSmsData(
            brandName = canonicalizeBrand(brand, knownBrands),
            amount = Money(amountMinor, Currency(currencyCode)),
            occurredAt = occurredAt,
        )
    }

    private fun derivePattern(body: String, raw: AiParsedSms, suggestion: ParsedSmsData): String? {
        val rawBrand = raw.brandName?.trim().orEmpty()
        val amountMinor = suggestion.amount?.amountMinor ?: return null
        val spans = deriveAiSpans(body, rawBrand, amountMinor) ?: return null
        return deriveTemplatePattern(body, spans)
    }

    private fun todayContext(): String {
        val today = clock.today(TimeZone.currentSystemDefault())
        val weekday = today.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
        return "$today, $weekday"
    }

    private companion object {
        const val MAX_KNOWN_BRANDS = 50

        /** One billion in major units — far above any plausible transaction, far below Long overflow. */
        const val MAX_AMOUNT_MINOR = 1_000_000_000_00L

        /** How far before the SMS arrival a model-claimed date is still believable. Bank alerts
         *  arrive near the transaction; anything older falls back to the arrival time and can
         *  be corrected in the transaction editor. */
        val MAX_DATE_AGE = 7.days

        /** Free-text window: a typed or pasted note may legitimately describe last month. */
        val MAX_DATE_AGE_FREE_TEXT = 365.days
    }
}
