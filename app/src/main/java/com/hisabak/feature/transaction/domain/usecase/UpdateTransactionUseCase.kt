package com.hisabak.feature.transaction.domain.usecase

import com.hisabak.core.common.DomainResult
import com.hisabak.feature.transaction.domain.Transaction
import com.hisabak.feature.transaction.domain.TransactionRepository

class UpdateTransactionUseCase(
    private val repository: TransactionRepository,
) {
    suspend operator fun invoke(transaction: Transaction): DomainResult<Unit> =
        repository.upsert(transaction)
}
