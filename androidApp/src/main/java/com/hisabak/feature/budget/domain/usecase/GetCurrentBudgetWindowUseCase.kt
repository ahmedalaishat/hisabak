package com.hisabak.feature.budget.domain.usecase

import com.hisabak.feature.budget.domain.Budget
import com.hisabak.feature.budget.domain.BudgetWindow
import com.hisabak.feature.budget.domain.Reoccurrence
import com.hisabak.core.common.Clock
import com.hisabak.core.common.DateRange
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.yearsUntil

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
            val windowEnd = windowStart.plus(budget.period, unit).minus(1, DateTimeUnit.DAY)
            if (today <= windowEnd) {
                return BudgetWindow(DateRange(windowStart, windowEnd))
            }
            windowStart = windowStart.plus(budget.period, unit)
            if (budget.startAt.yearsUntil(windowStart) > MAX_YEARS_SAFETY) {
                return BudgetWindow(DateRange(windowStart, windowStart))
            }
        }
    }

    private companion object {
        const val MAX_YEARS_SAFETY = 200L
    }
}
