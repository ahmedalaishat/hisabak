package com.hisabak.testutil

import com.hisabak.feature.sms.domain.ai.AiParsedSms
import com.hisabak.feature.sms.domain.ai.RemoteParseClient
import com.hisabak.feature.sms.domain.ai.RemoteParseRequest

class FakeRemoteParseClient(
    override var isConfigured: Boolean = true,
    var result: AiParsedSms? = null,
) : RemoteParseClient {
    val requests = mutableListOf<RemoteParseRequest>()

    override suspend fun parse(request: RemoteParseRequest): AiParsedSms? {
        requests += request
        return result
    }
}
