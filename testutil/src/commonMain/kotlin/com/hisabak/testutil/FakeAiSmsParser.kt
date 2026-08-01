package com.hisabak.testutil

import com.hisabak.feature.sms.domain.ai.AiParsedSms
import com.hisabak.feature.sms.domain.ai.AiParserAvailability
import com.hisabak.feature.sms.domain.ai.AiSmsParser

class FakeAiSmsParser : AiSmsParser {
    var availability: AiParserAvailability = AiParserAvailability.Ready
    var result: AiParsedSms? = null
    val parsedBodies = mutableListOf<String>()
    var lastKnownBrands: List<String> = emptyList()
        private set

    override suspend fun availability(): AiParserAvailability = availability

    override suspend fun parse(body: String, knownBrands: List<String>): AiParsedSms? {
        parsedBodies += body
        lastKnownBrands = knownBrands
        return result
    }

    var freeTextResult: AiParsedSms? = null
    val parsedFreeTexts = mutableListOf<String>()
    var lastTodayIso: String? = null
        private set

    override suspend fun parseFreeText(
        text: String,
        knownBrands: List<String>,
        todayIso: String,
    ): AiParsedSms? {
        parsedFreeTexts += text
        lastKnownBrands = knownBrands
        lastTodayIso = todayIso
        return freeTextResult
    }
}
