package com.hisabak.feature.budget.domain.usecase

import com.hisabak.feature.budget.domain.Budget
import com.hisabak.feature.budget.domain.BudgetRepository
import kotlinx.coroutines.flow.Flow

class ObserveBudgetsUseCase(private val repository: BudgetRepository) {
    operator fun invoke(): Flow<List<Budget>> = repository.observeAll()
}
