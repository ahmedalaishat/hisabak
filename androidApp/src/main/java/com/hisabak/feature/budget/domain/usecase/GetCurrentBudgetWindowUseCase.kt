package com.hisabak.feature.budget.domain.usecase

import com.hisabak.feature.budget.domain.Budget
import com.hisabak.feature.budget.domain.BudgetWindow
import com.hisabak.feature.budget.domain.Reoccurrence
import com.hisabak.core.common.Clock
import com.hisabak.core.common.DateRange
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Mirrors Hisabi's Budget::getCurrentWindowStartAndEndDates.
 * CUSTOM → uses the explicit startAt/endAt.
 * Recurring → computes the window containing `today` by stepping forward from startAt
 *             in `period * unit` increments, where unit is derived from reoccurrence.
 */
class GetCurrentBudgetWindowUseCase(private val clock: Clock) {
    operator fun invoke(budget: Budget, today: LocalDate = clock.today()): BudgetWindow {
        if (budget.reoccurrence == Reoccurrence.CUSTOM) {
            return BudgetWindow(DateRange(budget.startAt, requireNotNull(budget.endAt)))
        }
        val unit = requireNotNull(budget.reoccurrence.unit)
        var windowStart = budget.startAt
        while (true) {
            val windowEnd = windowStart.plus(budget.period.toLong(), unit).minusDays(1)
            if (!today.isAfter(windowEnd)) {
                return BudgetWindow(DateRange(windowStart, windowEnd))
            }
            windowStart = windowStart.plus(budget.period.toLong(), unit)
            if (ChronoUnit.YEARS.between(budget.startAt, windowStart) > MAX_YEARS_SAFETY) {
                return BudgetWindow(DateRange(windowStart, windowStart))
            }
        }
    }

    private companion object {
        const val MAX_YEARS_SAFETY = 200L
    }
}
