package com.hisabak.feature.sms.domain

import com.hisabak.feature.brand.domain.usecase.FindOrCreateBrandUseCase
import com.hisabak.core.common.Clock
import com.hisabak.core.common.DomainError
import com.hisabak.core.common.DomainResult
import com.hisabak.feature.transaction.domain.Transaction
import com.hisabak.feature.transaction.domain.TransactionId
import com.hisabak.feature.transaction.domain.TransactionRepository
import kotlin.time.Instant

/**
 * Mirrors Hisabi's BusinessLogic\SmsTransactionProcessor.
 * Orchestrates: detect template → parse → find/create brand → create transaction → link sms.
 */
class SmsTransactionProcessor(
    private val detector: SmsTemplateDetector,
    private val parser: SmsParser,
    private val findOrCreateBrand: FindOrCreateBrandUseCase,
    private val transactionRepository: TransactionRepository,
    private val smsRepository: SmsRepository,
    private val clock: Clock,
) {
    suspend fun process(message: SmsMessage, defaultDate: Instant? = null): DomainResult<Transaction> {
        val template = detector.detect(message.body)
            ?: return DomainResult.Failure(DomainError.ValidationFailed("No SMS template matched"))

        val parsed = parser.parse(message.body, template).let { base ->
            if (base.occurredAt == null) base.copy(occurredAt = defaultDate ?: clock.now()) else base
        }

        val brandName = parsed.brandName
            ?: return DomainResult.Failure(DomainError.ValidationFailed("SMS parse missing brand"))
        val amount = parsed.amount
            ?: return DomainResult.Failure(DomainError.ValidationFailed("SMS parse missing amount"))

        return findOrCreateBrand(brandName).flatMap { brand ->
            val tx = Transaction(
                id = TransactionId.new(),
                amount = amount,
                brandId = brand.id,
                note = null,
                occurredAt = parsed.occurredAt ?: clock.now(),
                sourceSmsId = message.id.value,
            )
            transactionRepository.upsert(tx).flatMap {
                smsRepository.upsert(message.copy(parsed = parsed, transactionId = tx.id)).map { tx }
            }
        }
    }
}
