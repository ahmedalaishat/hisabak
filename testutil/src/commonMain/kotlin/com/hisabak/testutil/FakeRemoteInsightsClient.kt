package com.hisabak.testutil

import com.hisabak.feature.insights.domain.ai.InsightsRequestDto
import com.hisabak.feature.insights.domain.ai.InsightsResponseDto
import com.hisabak.feature.insights.domain.ai.RemoteInsightsClient

class FakeRemoteInsightsClient(
    override var isConfigured: Boolean = true,
    var result: InsightsResponseDto? = null,
) : RemoteInsightsClient {
    val requests = mutableListOf<InsightsRequestDto>()

    override suspend fun narrate(request: InsightsRequestDto): InsightsResponseDto? {
        requests += request
        return result
    }
}
