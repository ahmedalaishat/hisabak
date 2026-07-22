package com.hisabak.core.common

import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** A selectable reporting window shared by the dashboard summary cards. */
enum class SummaryPeriod {
    CURRENT_MONTH,
    LAST_MONTH,
    CURRENT_YEAR,
    LAST_YEAR,
    ALL;

    /** [start, end) dates for this period relative to [today], or null for [ALL]. */
    fun dateRange(today: LocalDate): Pair<LocalDate, LocalDate>? = when (this) {
        CURRENT_MONTH -> firstOfMonth(today).let { it to it.plus(1, DateTimeUnit.MONTH) }
        LAST_MONTH -> firstOfMonth(today).minus(1, DateTimeUnit.MONTH).let { it to it.plus(1, DateTimeUnit.MONTH) }
        CURRENT_YEAR -> firstOfYear(today).let { it to it.plus(1, DateTimeUnit.YEAR) }
        LAST_YEAR -> firstOfYear(today).minus(1, DateTimeUnit.YEAR).let { it to it.plus(1, DateTimeUnit.YEAR) }
        ALL -> null
    }

    /** [start, end) instant range relative to [today] in [zone], or null for [ALL]. */
    fun instantRange(today: LocalDate, zone: TimeZone): Pair<Instant, Instant>? =
        dateRange(today)?.let { (start, end) ->
            start.atStartOfDayIn(zone) to end.atStartOfDayIn(zone)
        }

    /** The equal-length window immediately before this one, for trend comparison. */
    fun previousInstantRange(today: LocalDate, zone: TimeZone): Pair<Instant, Instant>? {
        val previous = when (this) {
            CURRENT_MONTH, LAST_MONTH ->
                dateRange(today)?.let { (start, _) -> start.minus(1, DateTimeUnit.MONTH) to start }
            CURRENT_YEAR, LAST_YEAR ->
                dateRange(today)?.let { (start, _) -> start.minus(1, DateTimeUnit.YEAR) to start }
            ALL -> null
        } ?: return null
        return previous.first.atStartOfDayIn(zone) to previous.second.atStartOfDayIn(zone)
    }

    private fun firstOfMonth(date: LocalDate): LocalDate = LocalDate(date.year, date.month, 1)
    private fun firstOfYear(date: LocalDate): LocalDate = LocalDate(date.year, 1, 1)
}
