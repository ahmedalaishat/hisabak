package com.hisabak.feature.budget.domain.usecase

import com.hisabak.feature.budget.domain.BudgetId
import com.hisabak.feature.budget.domain.BudgetRepository
import com.hisabak.core.common.DomainResult

class DeleteBudgetUseCase(private val repository: BudgetRepository) {
    suspend operator fun invoke(id: BudgetId): DomainResult<Unit> = repository.delete(id)
}
