package com.hisabak.feature.transaction.domain.usecase

import com.hisabak.core.common.DomainResult
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.transaction.domain.TransactionRepository

/** Moves every transaction of one brand onto another — the data step of a brand merge. */
class ReassignBrandTransactionsUseCase(
    private val repository: TransactionRepository,
) {
    suspend operator fun invoke(from: BrandId, to: BrandId): DomainResult<Unit> =
        repository.reassignBrand(from, to)
}
