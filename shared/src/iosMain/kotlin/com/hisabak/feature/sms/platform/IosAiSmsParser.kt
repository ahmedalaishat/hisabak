package com.hisabak.feature.sms.platform

import com.hisabak.feature.sms.domain.ai.AiParsedSms
import com.hisabak.feature.sms.domain.ai.AiParserAvailability
import com.hisabak.feature.sms.domain.ai.AiSmsParser
import com.hisabak.feature.sms.domain.ai.parseAiIsoDate
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** [AiSmsParser] over the Swift Foundation Models bridge injected via `startIosApp`. */
class IosAiSmsParser(private val bridge: AiSmsBridge) : AiSmsParser {

    override suspend fun availability(): AiParserAvailability =
        if (bridge.isAvailable()) AiParserAvailability.Ready else AiParserAvailability.Unavailable

    override suspend fun parse(body: String, knownBrands: List<String>): AiParsedSms? =
        suspendCancellableCoroutine { continuation ->
            bridge.parse(body, knownBrands) { result ->
                continuation.resume(
                    result?.let {
                        AiParsedSms(
                            brandName = it.brandName,
                            amountMinor = if (it.hasAmount) it.amountMinor else null,
                            currencyCode = it.currencyCode,
                            occurredAtEpochMillis = it.dateIso?.let(::parseAiIsoDate),
                        )
                    },
                )
            }
        }
}
