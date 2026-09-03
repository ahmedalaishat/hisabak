package com.hisabak.testutil

import com.hisabak.feature.insights.domain.ai.AskRequestDto
import com.hisabak.feature.insights.domain.ai.AskResponseDto
import com.hisabak.feature.insights.domain.ai.InsightsRequestDto
import com.hisabak.feature.insights.domain.ai.InsightsResponseDto
import com.hisabak.feature.insights.domain.ai.RemoteInsightsClient

class FakeRemoteInsightsClient(
    override var isConfigured: Boolean = true,
    var result: InsightsResponseDto? = null,
    var answer: AskResponseDto? = AskResponseDto(answer = "Dining drove it.", onTopic = true),
) : RemoteInsightsClient {
    val requests = mutableListOf<InsightsRequestDto>()
    val asks = mutableListOf<Pair<AskRequestDto, String>>()

    override suspend fun ask(request: AskRequestDto, installId: String): AskResponseDto? {
        asks += request to installId
        return answer
    }

    override suspend fun narrate(request: InsightsRequestDto): InsightsResponseDto? {
        requests += request
        return result
    }
}
