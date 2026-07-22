package com.hisabak.feature.transaction.domain.usecase

import com.hisabak.feature.transaction.domain.Transaction
import com.hisabak.feature.transaction.domain.TransactionFilter
import com.hisabak.feature.transaction.domain.TransactionRepository
import kotlinx.coroutines.flow.Flow

class ObserveTransactionsUseCase(private val repository: TransactionRepository) {
    operator fun invoke(filter: TransactionFilter = TransactionFilter.NONE): Flow<List<Transaction>> =
        repository.observe(filter)
}
