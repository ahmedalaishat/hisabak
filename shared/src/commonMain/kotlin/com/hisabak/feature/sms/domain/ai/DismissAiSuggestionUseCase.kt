package com.hisabak.feature.sms.domain.ai

import com.hisabak.core.common.DomainResult
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.feature.sms.domain.SmsRepository

class DismissAiSuggestionUseCase(
    private val smsRepository: SmsRepository,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(messageId: SmsMessageId): DomainResult<Unit> {
        val message = when (val result = smsRepository.getById(messageId)) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return result
        }
        if (message.suggested == null) return DomainResult.Success(Unit)
        analytics.log(AnalyticsEvent.AiSuggestionDismissed)
        return smsRepository.upsert(message.copy(suggested = null))
    }
}
