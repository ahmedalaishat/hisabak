package com.hisabak.feature.budget.domain.usecase

import com.hisabak.feature.budget.domain.Budget
import com.hisabak.feature.budget.domain.BudgetProgress
import com.hisabak.feature.budget.domain.BudgetRepository
import com.hisabak.core.common.Clock
import java.time.LocalDate

class CalculateBudgetProgressUseCase(
    private val repository: BudgetRepository,
    private val getCurrentWindow: GetCurrentBudgetWindowUseCase,
    private val clock: Clock,
) {
    suspend operator fun invoke(budget: Budget, today: LocalDate = clock.today()): BudgetProgress {
        val window = getCurrentWindow(budget, today)
        val spent = repository.sumTransactionsIn(budget, window)
        val totalDays = window.totalDays.coerceAtLeast(1)
        val elapsed = window.elapsedDays(today).toDouble()
        val elapsedPct = (elapsed * 100.0 / totalDays).coerceIn(0.0, 100.0)
        return BudgetProgress(
            budgetId = budget.id,
            window = window,
            limit = budget.amount,
            spent = spent,
            remainingDays = window.remainingDays(today),
            elapsedDaysPercentage = elapsedPct,
        )
    }
}
