package com.hisabak.feature.sms.domain.ai

import com.hisabak.core.common.DomainError
import com.hisabak.core.common.DomainResult
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.feature.notification.domain.CategoryLimitMonitor
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.feature.sms.domain.SmsRepository
import com.hisabak.feature.sms.domain.SmsTemplateId
import com.hisabak.feature.sms.domain.SmsTransactionProcessor
import com.hisabak.feature.sms.domain.template.SynthesizeTemplateUseCase
import com.hisabak.feature.transaction.domain.Transaction

/**
 * Promotes a stored AI suggestion into a real transaction through the trusted pipeline tail
 * ([SmsTransactionProcessor.commit]) — one write creates the transaction, links the message,
 * and records the suggestion as its confirmed parse. The suggestion itself is deliberately
 * retained: on a linked message it serves as the "AI parsed" provenance marker in the inbox.
 * No recorded notification: the user is in-app, watching the row flip to Linked.
 *
 * Confirming also installs the template derived at suggest time, so the next message of this bank
 * format never needs the model. That is a bonus, never a precondition — a synthesis failure leaves
 * the transaction untouched and simply reports no learned template.
 */
class ConfirmAiSuggestionUseCase(
    private val smsRepository: SmsRepository,
    private val processor: SmsTransactionProcessor,
    private val limitMonitor: CategoryLimitMonitor,
    private val synthesizeTemplate: SynthesizeTemplateUseCase,
    private val analytics: Analytics,
) {
    /** [automatic] means the auto-confirm gate decided, not the user; the row is labelled with it. */
    suspend operator fun invoke(
        messageId: SmsMessageId,
        automatic: Boolean = false,
    ): DomainResult<ConfirmedSuggestion> {
        val message = when (val result = smsRepository.getById(messageId)) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return result
        }
        if (message.isLinked) {
            return DomainResult.Failure(DomainError.Conflict("Message already linked"))
        }
        val suggestion = message.suggested
            ?: return DomainResult.Failure(DomainError.ValidationFailed("No suggestion to confirm"))

        val transaction = when (val result = processor.commit(message, suggestion, automatic)) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return result
        }
        analytics.log(AnalyticsEvent.AiSuggestionConfirmed(transaction.amount))
        limitMonitor.evaluateNow()

        val learned = message.suggestedPattern
            ?.let { synthesizeTemplate(message.body, it).getOrNull() }
        return DomainResult.Success(ConfirmedSuggestion(transaction, learned?.id))
    }
}

/** [learnedTemplateId] is set only when confirming also installed a template — the inbox offers Undo for it. */
data class ConfirmedSuggestion(
    val transaction: Transaction,
    val learnedTemplateId: SmsTemplateId?,
)
