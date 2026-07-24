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
}
