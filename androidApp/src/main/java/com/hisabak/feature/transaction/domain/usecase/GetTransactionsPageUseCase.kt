package com.hisabak.feature.transaction.domain.usecase

import com.hisabak.core.common.DomainResult
import com.hisabak.feature.transaction.domain.PagedTransactions
import com.hisabak.feature.transaction.domain.TransactionFilter
import com.hisabak.feature.transaction.domain.TransactionRepository

class GetTransactionsPageUseCase(private val repository: TransactionRepository) {
    suspend operator fun invoke(
        filter: TransactionFilter = TransactionFilter.NONE,
        page: Int = 1,
        perPage: Int = DEFAULT_PER_PAGE,
    ): DomainResult<PagedTransactions> = repository.getPage(filter, page, perPage)

    companion object {
        const val DEFAULT_PER_PAGE = 100
    }
}
