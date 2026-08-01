package com.hisabak.feature.transaction.domain.usecase

import com.hisabak.core.common.Clock
import com.hisabak.core.common.Currency
import com.hisabak.core.common.DomainError
import com.hisabak.core.common.DomainResult
import com.hisabak.core.common.Money
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.brand.domain.BrandRepository
import com.hisabak.feature.sms.domain.ai.AiParsedSms
import com.hisabak.feature.sms.domain.ai.AiParserAvailability
import com.hisabak.feature.sms.domain.ai.AiSmsParser
import com.hisabak.feature.sms.domain.ai.canonicalizeBrand
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/**
 * Turns a typed note ("100 at noon", "lunch 45 yesterday") into a pre-fill for the transaction
 * sheet via the on-device model. Confirm-first: the result only fills the form — saving is the
 * user's confirmation. The free-text sanitize differs from the SMS one: relative dates are
 * legitimate here (the prompt carries today's date), so the plausibility window reaches
 * [MAX_DATE_AGE] back — but never into the future.
 */
class ParseSmartInputUseCase(
    private val aiParser: AiSmsParser,
    private val brandRepository: BrandRepository,
    private val defaultCurrency: Currency,
    private val clock: Clock,
    private val analytics: Analytics,
) {
    /** [brandId] is null when no existing brand matched — the sheet then offers to create [brandName]. */
    data class SmartParse(
        val brandId: BrandId?,
        val brandName: String,
        val amount: Money,
        val occurredAt: Instant,
    )

    suspend operator fun invoke(text: String): DomainResult<SmartParse> {
        if (aiParser.availability() != AiParserAvailability.Ready) {
            analytics.log(AnalyticsEvent.SmartParseFailed("unavailable"))
            return DomainResult.Failure(DomainError.ValidationFailed("AI parser unavailable"))
        }

        analytics.log(AnalyticsEvent.SmartParseAttempted)
        val knownBrands = brandRepository.namesByUsage(MAX_KNOWN_BRANDS)
        val raw = aiParser.parseFreeText(text, knownBrands, todayContext())
        if (raw == null) {
            analytics.log(AnalyticsEvent.SmartParseFailed("model_empty"))
            return DomainResult.Failure(DomainError.ValidationFailed("AI parse produced nothing"))
        }
        val parsed = sanitize(raw, knownBrands)
        if (parsed == null) {
            analytics.log(AnalyticsEvent.SmartParseFailed("incomplete"))
            return DomainResult.Failure(DomainError.ValidationFailed("AI parse incomplete"))
        }

        analytics.log(AnalyticsEvent.SmartParseSucceeded(parsed.amount, matchedBrand = parsed.brandId != null))
        return DomainResult.Success(parsed)
    }

    private suspend fun sanitize(raw: AiParsedSms, knownBrands: List<String>): SmartParse? {
        val brand = raw.brandName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        // Ceiling: the model controls this value; an absurd magnitude would pre-fill an amount
        // Save can only reject confusingly. Reject it here as incomplete instead.
        val amountMinor = raw.amountMinor?.takeIf { it in 1..MAX_AMOUNT_MINOR } ?: return null
        val currencyCode = raw.currencyCode?.trim()?.uppercase()
            ?.takeIf { code -> code.length == 3 && code.all { it in 'A'..'Z' } }
            ?: defaultCurrency.code
        val now = clock.now()
        val occurredAt = raw.occurredAtEpochMillis
            ?.let(Instant::fromEpochMilliseconds)
            // The prompt resolves "yesterday"-style wording, so a wide back window is right;
            // the future is always a hallucination ("never produce a future date" is prompted,
            // but a slack day absorbs the model dating "today" at a later wall-clock hour).
            ?.takeIf { it >= now - MAX_DATE_AGE && it <= now + 1.days }
            ?: now
        val canonical = canonicalizeBrand(brand, knownBrands)
        // Exact-name lookup only: canonicalize already did the fuzzy work against the usage-top
        // brands, and a fuzzy DB lookup here could silently pick a different brand than shown.
        val matched = brandRepository.findByNameLike(canonical)
            ?.takeIf { it.name.equals(canonical, ignoreCase = true) }
        return SmartParse(
            brandId = matched?.id,
            brandName = matched?.name ?: canonical,
            amount = Money(amountMinor, Currency(currencyCode)),
            occurredAt = occurredAt,
        )
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

        /** How far back a typed note may plausibly reach ("last month's gym fee"). */
        val MAX_DATE_AGE = 365.days
    }
}
