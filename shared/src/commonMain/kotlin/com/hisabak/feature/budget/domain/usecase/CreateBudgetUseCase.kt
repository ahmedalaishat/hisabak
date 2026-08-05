package com.hisabak.feature.budget.domain.usecase

import com.hisabak.feature.budget.domain.Budget
import com.hisabak.feature.budget.domain.BudgetId
import com.hisabak.feature.budget.domain.BudgetRepository
import com.hisabak.feature.budget.domain.Reoccurrence
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.core.common.DomainResult
import com.hisabak.core.common.Money
import kotlinx.datetime.LocalDate

class CreateBudgetUseCase(
    private val repository: BudgetRepository,
) {
    suspend operator fun invoke(
        name: String,
        amount: Money,
        startAt: LocalDate,
        endAt: LocalDate?,
        reoccurrence: Reoccurrence,
        period: Int = 1,
        saving: Boolean = false,
        categoryIds: Set<CategoryId> = emptySet(),
    ): DomainResult<Budget> {
        val budget = Budget(
            id = BudgetId.new(),
            name = name,
            amount = amount,
            startAt = startAt,
            endAt = endAt,
            saving = saving,
            period = period,
            reoccurrence = reoccurrence,
            categoryIds = categoryIds,
        )
        return repository.upsert(budget).map { budget }
    }
}
