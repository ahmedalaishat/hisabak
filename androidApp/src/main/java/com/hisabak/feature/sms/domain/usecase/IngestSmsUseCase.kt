package com.hisabak.feature.sms.domain.usecase

import com.hisabak.core.common.Clock
import com.hisabak.core.common.DomainError
import com.hisabak.core.common.DomainResult
import com.hisabak.feature.sms.domain.SmsMessage
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.feature.sms.domain.SmsRepository
import com.hisabak.feature.sms.domain.SmsTransactionProcessor
import com.hisabak.feature.transaction.domain.Transaction
import kotlin.time.Instant

class IngestSmsUseCase(
    private val smsRepository: SmsRepository,
    private val processor: SmsTransactionProcessor,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        body: String,
        receivedAt: Instant? = null,
    ): DomainResult<Transaction> {
        // Broadcasts can be redelivered; (body, receivedAt) is a stable key, so skip a capture
        // we already stored. Manual paste passes no receivedAt and is never deduped.
        if (receivedAt != null && smsRepository.existsByContent(body, receivedAt)) {
            return DomainResult.Failure(DomainError.Conflict("Duplicate SMS ignored"))
        }
        val occurredFallback = receivedAt ?: clock.now()
        val message = SmsMessage(
            id = SmsMessageId.new(),
            body = body,
            receivedAt = occurredFallback,
        )
        // Pass the received time so an SMS with no parseable date is dated when it arrived,
        // not at clock.now().
        return smsRepository.upsert(message)
            .flatMap { processor.process(message, defaultDate = occurredFallback) }
    }
}
