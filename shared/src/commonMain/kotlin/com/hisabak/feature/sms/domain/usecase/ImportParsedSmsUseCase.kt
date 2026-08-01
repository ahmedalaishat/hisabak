package com.hisabak.feature.sms.domain.usecase

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
 * Re-imports a message that is parsed but not linked — the state a captured message returns to
 * when its transaction is deleted ("goes back to the SMS inbox so you can import it again").
 * The stored parse is trusted as-is; [SmsTransactionProcessor.commit] creates the transaction
 * and re-links the message.
 */
class ImportParsedSmsUseCase(
    private val smsRepository: SmsRepository,
    private val processor: SmsTransactionProcessor,
    private val limitMonitor: CategoryLimitMonitor,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(id: SmsMessageId): DomainResult<Transaction> {
        val message = when (val result = smsRepository.getById(id)) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return DomainResult.Failure(result.error)
        }
        if (message.isLinked) {
            return DomainResult.Failure(DomainError.Conflict("Message already imported"))
        }
        val parsed = message.parsed?.takeIf { it.isComplete }
            ?: return DomainResult.Failure(DomainError.ValidationFailed("Message has no complete parse"))
        return processor.commit(message, parsed).also { result ->
            if (result is DomainResult.Success) {
                analytics.log(AnalyticsEvent.SmsCaptured(source = "reimport", amount = result.value.amount))
                limitMonitor.evaluateNow()
            }
        }
    }
}
