package com.hisabak.feature.sms.platform

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.hisabak.feature.sms.domain.ai.AiParsedSms
import com.hisabak.feature.sms.domain.ai.AiParserAvailability
import com.hisabak.feature.sms.domain.ai.AiSmsParser
import com.hisabak.feature.sms.domain.ai.parseAiIsoDate
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/**
 * [AiSmsParser] over Gemini Nano via the ML Kit GenAI Prompt API. The model is OS-managed
 * (AICore) — nothing ships in the APK, and inference never leaves the device. Only recent
 * flagship devices have the feature; everywhere else [availability] reports Unavailable and the
 * app behaves exactly as before. A DOWNLOADABLE model triggers a background download and stays
 * Unavailable for this session.
 */
class GeminiNanoSmsParser(private val appScope: CoroutineScope) : AiSmsParser {

    private val model by lazy { Generation.getClient() }
    private var ready = false

    override suspend fun availability(): AiParserAvailability {
        if (ready) return AiParserAvailability.Ready
        val status = runCatching { model.checkStatus() }.getOrDefault(FeatureStatus.UNAVAILABLE)
        return when (status) {
            FeatureStatus.AVAILABLE -> {
                ready = true
                AiParserAvailability.Ready
            }
            FeatureStatus.DOWNLOADABLE -> {
                appScope.launch { runCatching { model.download().collect {} } }
                AiParserAvailability.Unavailable
            }
            else -> AiParserAvailability.Unavailable
        }
    }

    override suspend fun parse(body: String, knownBrands: List<String>): AiParsedSms? = runCatching {
        val response = model.generateContent(promptFor(body, knownBrands))
        response.candidates.firstOrNull()?.text?.let(::decode)
    }.getOrNull()

    override suspend fun parseFreeText(
        text: String,
        knownBrands: List<String>,
        todayIso: String,
    ): AiParsedSms? = runCatching {
        val response = model.generateContent(freeTextPromptFor(text, knownBrands, todayIso))
        response.candidates.firstOrNull()?.text?.let(::decode)
    }.getOrNull()

    private fun promptFor(body: String, knownBrands: List<String>) = buildString {
        appendLine(
            """
            You extract bank transaction data from SMS messages. Messages may be in English, Arabic, or both.
            Reply with ONLY a JSON object, exactly this shape:
            {"brand": string or null, "amount": number or null, "currency": string or null, "date": string or null}
            Rules:
            - brand: the merchant or sender name only, cleaned of locations and reference codes (e.g. "CARREFOUR", not "CARREFOUR, DUBAI, ARE").
            - amount: the transaction amount as a positive decimal number without separators.
            - currency: the ISO 4217 code such as "AED" or "USD"; null if not stated.
            - date: the transaction date and time in ISO 8601 format (e.g. 2026-07-24T10:30:00), ONLY if a date is written in the message itself; if the message has no date, use null - never guess or invent one.
            - If the text is not a bank transaction message, use null for every field.
            """.trimIndent(),
        )
        if (knownBrands.isNotEmpty()) {
            appendLine("- Known brands: ${knownBrands.joinToString(", ")}")
            appendLine("- If the merchant matches a known brand - even with typos, different casing, or an abbreviation - use that brand name exactly as listed.")
        }
        appendLine()
        appendLine("Message:")
        append(body)
    }

    private fun freeTextPromptFor(text: String, knownBrands: List<String>, todayIso: String) = buildString {
        appendLine(
            """
            You extract a spending or income record from a short note a person typed, in English, Arabic, or both (e.g. "100 at noon", "lunch 45 yesterday", "salary 15k").
            Reply with ONLY a JSON object, exactly this shape:
            {"brand": string or null, "amount": number or null, "currency": string or null, "date": string or null}
            Rules:
            - Today is $todayIso.
            - brand: the merchant, store, or payee named in the note. null if none is named.
            - amount: the amount as a positive decimal number without separators; expand shorthand like "15k" to 15000.
            - currency: the ISO 4217 code ONLY if the note states a currency; null otherwise.
            - date: resolve wording like "yesterday", "last friday", or an explicit date against today's date, as ISO 8601 (e.g. 2026-07-31); null if the note has no date wording. Never produce a future date.
            - If the note doesn't describe a purchase, payment, or income, use null for every field.
            """.trimIndent(),
        )
        if (knownBrands.isNotEmpty()) {
            appendLine("- Known brands: ${knownBrands.joinToString(", ")}")
            appendLine("- If the named merchant matches a known brand - even with typos, different casing, or an abbreviation - use that brand name exactly as listed.")
        }
        appendLine()
        appendLine("Note:")
        append(text)
    }

    /** Defensive decode: models wrap JSON in prose/fences at will; null on anything unusable. */
    private fun decode(raw: String): AiParsedSms? {
        val start = raw.indexOf('{').takeIf { it >= 0 } ?: return null
        val end = raw.lastIndexOf('}').takeIf { it > start } ?: return null
        val json = runCatching { Json.parseToJsonElement(raw.substring(start, end + 1)).jsonObject }
            .getOrNull() ?: return null

        // JsonNull is a JsonPrimitive with isString=false and doubleOrNull=null, so it falls
        // through every branch below without special-casing.
        fun string(name: String) = (json[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

        return AiParsedSms(
            brandName = string("brand"),
            amountMinor = (json["amount"] as? JsonPrimitive)?.doubleOrNull?.let { (it * 100).roundToLong() },
            currencyCode = string("currency"),
            occurredAtEpochMillis = string("date")?.let(::parseAiIsoDate),
        )
    }
}
