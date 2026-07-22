package com.hisabak.feature.budget.domain

import com.hisabak.core.common.DomainResult
import com.hisabak.core.common.Money
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    fun observeAll(): Flow<List<Budget>>
    suspend fun getById(id: BudgetId): DomainResult<Budget>
    suspend fun upsert(budget: Budget): DomainResult<Unit>
    suspend fun delete(id: BudgetId): DomainResult<Unit>
    suspend fun sumTransactionsIn(budget: Budget, window: BudgetWindow): Money
}
