package com.hisabak.feature.sms.domain.ai

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/**
 * On-device generative parser for bank SMS that no regex template matched. Platform
 * implementations (Gemini Nano on Android, Apple Foundation Models on iOS) own their prompt and
 * output-schema idioms; this contract fixes the semantics: [AiParsedSms.brandName] is a cleaned
 * merchant name, [AiParsedSms.amountMinor] the amount in minor units, [AiParsedSms.currencyCode]
 * an ISO code or null, and text that isn't a bank transaction yields null fields or a null result.
 * Implementations never throw — model failure is null. Inference runs fully on device; the SMS
 * text must never leave the phone.
 */
interface AiSmsParser {
    suspend fun availability(): AiParserAvailability
    suspend fun parse(body: String): AiParsedSms?
}

enum class AiParserAvailability { Ready, Unavailable }

/** Raw model output, primitive-typed so it crosses the Swift bridge cleanly; sanitized in common code. */
data class AiParsedSms(
    val brandName: String?,
    val amountMinor: Long?,
    val currencyCode: String?,
    val occurredAtEpochMillis: Long?,
)

/**
 * Lenient ISO-8601 parse for model-emitted dates (instant, local date-time, or bare date in the
 * device zone) — shared by the platform adapters so both accept the same shapes. Null when the
 * model produced something else; the sanitize step then falls back to the SMS arrival time.
 */
fun parseAiIsoDate(
    text: String,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): Long? {
    runCatching { return Instant.parse(text).toEpochMilliseconds() }
    runCatching { return LocalDateTime.parse(text).toInstant(zone).toEpochMilliseconds() }
    runCatching { return LocalDate.parse(text).atStartOfDayIn(zone).toEpochMilliseconds() }
    return null
}
