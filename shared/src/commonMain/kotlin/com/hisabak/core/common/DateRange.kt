package com.hisabak.core.common

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.yearMonth

data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    init {
        require(endInclusive >= start) {
            "DateRange end ($endInclusive) must not precede start ($start)"
        }
    }

    val totalDays: Long get() = start.daysUntil(endInclusive) + 1L

    operator fun contains(date: LocalDate): Boolean = date in start..endInclusive

    companion object {
        fun month(date: LocalDate): DateRange =
            DateRange(date.yearMonth.firstDay, date.yearMonth.lastDay)
    }
}
