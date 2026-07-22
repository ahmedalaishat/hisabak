package com.hisabak.core.common

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    init {
        require(!endInclusive.isBefore(start)) {
            "DateRange end ($endInclusive) must not precede start ($start)"
        }
    }

    val totalDays: Long get() = ChronoUnit.DAYS.between(start, endInclusive) + 1

    operator fun contains(date: LocalDate): Boolean =
        !date.isBefore(start) && !date.isAfter(endInclusive)

    companion object {
        fun month(date: LocalDate): DateRange =
            DateRange(date.withDayOfMonth(1), date.withDayOfMonth(date.lengthOfMonth()))
    }
}
