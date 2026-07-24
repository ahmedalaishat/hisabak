package com.hisabak.feature.sms.domain.ai

import com.hisabak.core.common.DomainError
import com.hisabak.core.common.DomainResult
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.feature.notification.domain.CategoryLimitMonitor
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.feature.sms.domain.SmsRepository
import com.hisabak.feature.sms.domain.SmsTransactionProcessor
import com.hisabak.feature.transaction.domain.Transaction

/**
 * Promotes a stored AI suggestion into a real transaction through the trusted pipeline tail
 * ([SmsTransactionProcessor.commit]) — one write creates the transaction, links the message,
 * records the suggestion as its confirmed parse, and clears the suggestion. No recorded
 * notification: the user is in-app, watching the row flip to Linked.
 */
class ConfirmAiSuggestionUseCase(
    private val smsRepository: SmsRepository,
    private val processor: SmsTransactionProcessor,
    private val limitMonitor: CategoryLimitMonitor,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(messageId: SmsMessageId): DomainResult<Transaction> {
        val message = when (val result = smsRepository.getById(messageId)) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return result
        }
        if (message.isLinked) {
            return DomainResult.Failure(DomainError.Conflict("Message already linked"))
        }
        val suggestion = message.suggested
            ?: return DomainResult.Failure(DomainError.ValidationFailed("No suggestion to confirm"))

        val result = processor.commit(message.copy(suggested = null), suggestion)
        if (result is DomainResult.Success) {
            analytics.log(AnalyticsEvent.AiSuggestionConfirmed(result.value.amount))
            limitMonitor.evaluateNow()
        }
        return result
    }
}
