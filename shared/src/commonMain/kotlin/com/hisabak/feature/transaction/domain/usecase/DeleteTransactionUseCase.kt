package com.hisabak.feature.transaction.domain.usecase

import com.hisabak.core.common.DomainResult
import com.hisabak.feature.sms.domain.SmsRepository
import com.hisabak.feature.transaction.domain.TransactionId
import com.hisabak.feature.transaction.domain.TransactionRepository

/**
 * Deletes the transaction and drops the back-reference any captured SMS holds to it. Without the
 * unlink the message would still read as Linked in the inbox while pointing at a row that no
 * longer exists; cleared, it falls back to Parsed and can be imported again.
 */
class DeleteTransactionUseCase(
    private val repository: TransactionRepository,
    private val smsRepository: SmsRepository,
) {
    suspend operator fun invoke(id: TransactionId): DomainResult<Unit> =
        repository.delete(id).flatMap { smsRepository.unlinkTransaction(id) }
}
