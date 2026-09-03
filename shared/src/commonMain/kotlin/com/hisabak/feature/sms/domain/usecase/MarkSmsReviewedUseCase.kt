package com.hisabak.feature.sms.domain.usecase

import com.hisabak.core.common.DomainResult
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.feature.sms.domain.SmsRepository

/**
 * Discharges the "Unreviewed" tag on a row the auto-confirm gate saved, once the user has opened
 * the transaction it created.
 *
 * Only `autoConfirmed` rows carry the tag, so nothing else is worth a write: marking a
 * row the user confirmed themselves would be a no-op the inbox never reads. Idempotent, so a
 * second visit is free.
 */
class MarkSmsReviewedUseCase(
    private val smsRepository: SmsRepository,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(id: SmsMessageId): DomainResult<Unit> {
        val message = when (val result = smsRepository.getById(id)) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return DomainResult.Failure(result.error)
        }
        if (!message.autoConfirmed || message.reviewed) return DomainResult.Success(Unit)

        return smsRepository.upsert(message.copy(reviewed = true)).also { result ->
            if (result is DomainResult.Success) analytics.log(AnalyticsEvent.AutoConfirmReviewed)
        }
    }
}
