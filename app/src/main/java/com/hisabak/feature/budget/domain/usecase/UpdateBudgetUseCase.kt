package com.hisabak.feature.budget.domain.usecase

import com.hisabak.feature.budget.domain.Budget
import com.hisabak.feature.budget.domain.BudgetRepository
import com.hisabak.core.common.DomainResult

class UpdateBudgetUseCase(
    private val repository: BudgetRepository,
) {
    suspend operator fun invoke(budget: Budget): DomainResult<Unit> =
        repository.upsert(budget)
}
