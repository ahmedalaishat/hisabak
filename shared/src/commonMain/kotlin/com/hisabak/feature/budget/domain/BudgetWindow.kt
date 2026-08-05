package com.hisabak.feature.budget.domain

import com.hisabak.core.common.DateRange
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

data class BudgetWindow(val range: DateRange) {
    val start: LocalDate get() = range.start
    val end: LocalDate get() = range.endInclusive
    val totalDays: Long get() = range.totalDays

    fun remainingDays(today: LocalDate): Long =
        if (today > end) 0L else today.daysUntil(end) + 1L

    fun elapsedDays(today: LocalDate): Long = when {
        today < start -> 0L
        today > end -> totalDays
        else -> start.daysUntil(today).toLong()
    }
}
