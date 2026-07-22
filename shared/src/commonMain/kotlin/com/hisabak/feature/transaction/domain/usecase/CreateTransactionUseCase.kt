package com.hisabak.feature.transaction.domain.usecase

import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.core.common.Clock
import com.hisabak.core.common.DomainResult
import com.hisabak.core.common.Money
import com.hisabak.feature.transaction.domain.Transaction
import com.hisabak.feature.transaction.domain.TransactionId
import com.hisabak.feature.transaction.domain.TransactionRepository
import kotlin.time.Instant

class CreateTransactionUseCase(
    private val repository: TransactionRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        amount: Money,
        brandId: BrandId,
        note: String? = null,
        occurredAt: Instant? = null,
        sourceSmsId: String? = null,
    ): DomainResult<Transaction> {
        val tx = Transaction(
            id = TransactionId.new(),
            amount = amount,
            brandId = brandId,
            note = note,
            occurredAt = occurredAt ?: clock.now(),
            sourceSmsId = sourceSmsId,
        )
        return repository.upsert(tx).map { tx }
    }
}
