package com.hisabak.feature.sms.domain.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire types for the parse service, shared by both platform clients so the two transports can
 * never drift apart. Field names are snake_case to match the Python service's schema.
 */
@Serializable
data class ParseRequestDto(
    val text: String,
    @SerialName("known_brands") val knownBrands: List<String>,
    @SerialName("today_iso") val todayIso: String?,
    @SerialName("free_text") val freeText: Boolean,
)

@Serializable
data class ParseResponseDto(
    val brand: String? = null,
    @SerialName("amount_minor") val amountMinor: Long? = null,
    val currency: String? = null,
    @SerialName("date_iso") val dateIso: String? = null,
) {
    /** Straight mapping — the shared `sanitize` step in `SuggestAiParseUseCase` owns acceptance,
     *  exactly as it does for the on-device parsers, so the transport applies no rules of its own. */
    fun toDomain(): AiParsedSms = AiParsedSms(
        brandName = brand,
        amountMinor = amountMinor,
        currencyCode = currency,
        occurredAtEpochMillis = dateIso?.let(::parseAiIsoDate),
    )
}
