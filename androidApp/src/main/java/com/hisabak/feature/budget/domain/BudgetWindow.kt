package com.hisabak.feature.budget.domain

import com.hisabak.core.common.DateRange
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class BudgetWindow(val range: DateRange) {
    val start: LocalDate get() = range.start
    val end: LocalDate get() = range.endInclusive
    val totalDays: Long get() = range.totalDays

    fun remainingDays(today: LocalDate): Long =
        if (today.isAfter(end)) 0L else ChronoUnit.DAYS.between(today, end) + 1

    fun elapsedDays(today: LocalDate): Long = when {
        today.isBefore(start) -> 0L
        today.isAfter(end) -> totalDays
        else -> ChronoUnit.DAYS.between(start, today)
    }
}
