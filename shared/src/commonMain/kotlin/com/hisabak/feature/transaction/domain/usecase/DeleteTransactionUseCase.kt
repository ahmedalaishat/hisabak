package com.hisabak.feature.transaction.domain.usecase

import com.hisabak.core.common.DomainResult
import com.hisabak.feature.transaction.domain.TransactionId
import com.hisabak.feature.transaction.domain.TransactionRepository

class DeleteTransactionUseCase(private val repository: TransactionRepository) {
    suspend operator fun invoke(id: TransactionId): DomainResult<Unit> = repository.delete(id)
}
