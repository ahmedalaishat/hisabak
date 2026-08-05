package com.hisabak.feature.budget.domain

import com.hisabak.core.common.Money

data class BudgetProgress(
    val budgetId: BudgetId,
    val window: BudgetWindow,
    val limit: Money,
    val spent: Money,
    val remainingDays: Long,
    val elapsedDaysPercentage: Double,
) {
    val remainingToSpend: Money get() = limit - spent

    val totalSpentPercentage: Double get() =
        if (limit.isZero) 0.0 else (spent.amountMinor * 100.0 / limit.amountMinor)

    val totalMarginPerDay: Money? get() =
        if (remainingDays > 0) Money(remainingToSpend.amountMinor / remainingDays, limit.currency) else null
}
