package com.hisabak.core.common

import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth

/**
 * The reporting window every date-scoped screen shares — the dashboard, the insights review, and
 * the transaction list all read the one selection in `PeriodSelection`.
 *
 * Periods are **absolute** (September 2026, not "this month"): stepping back through history needs
 * an address for every month, and a relative period would silently change meaning at midnight on
 * the first. "This month" is just [thisMonth] at the moment it is picked.
 */
sealed interface SummaryPeriod {
    data class Month(val month: YearMonth) : SummaryPeriod

    data class Year(val year: Int) : SummaryPeriod

    /** Both ends inclusive, the way a date-range picker reads them. */
    data class Custom(val start: LocalDate, val endInclusive: LocalDate) : SummaryPeriod {
        init {
            require(start <= endInclusive) { "start $start is after end $endInclusive" }
        }

        val lengthDays: Int get() = start.daysUntil(endInclusive) + 1
    }

    data object All : SummaryPeriod

    /** [start, end) dates, or null for [All]. */
    val window: Pair<LocalDate, LocalDate>?
        get() = when (this) {
            is Month -> month.firstDay to month.plus(1, DateTimeUnit.MONTH).firstDay
            is Year -> LocalDate(year, 1, 1) to LocalDate(year + 1, 1, 1)
            is Custom -> start to endInclusive.plus(1, DateTimeUnit.DAY)
            All -> null
        }

    /** [start, end) as instants at local midnight in [zone], or null for [All]. */
    fun instantRange(zone: TimeZone): Pair<Instant, Instant>? =
        window?.let { (start, end) -> start.atStartOfDayIn(zone) to end.atStartOfDayIn(zone) }

    /** The equal-length window immediately before this one — the comparison base and the ‹ step. */
    val previous: SummaryPeriod?
        get() = when (this) {
            is Month -> Month(month.minus(1, DateTimeUnit.MONTH))
            is Year -> Year(year - 1)
            is Custom -> Custom(start.minus(lengthDays, DateTimeUnit.DAY), start.minus(1, DateTimeUnit.DAY))
            All -> null
        }

    /** The equal-length window immediately after this one — the › step. */
    val next: SummaryPeriod?
        get() = when (this) {
            is Month -> Month(month.plus(1, DateTimeUnit.MONTH))
            is Year -> Year(year + 1)
            is Custom -> Custom(endInclusive.plus(1, DateTimeUnit.DAY), endInclusive.plus(lengthDays, DateTimeUnit.DAY))
            All -> null
        }

    fun previousInstantRange(zone: TimeZone): Pair<Instant, Instant>? = previous?.instantRange(zone)

    /** › is offered only while the next window has started — the future has nothing to show. */
    fun canStepForward(today: LocalDate): Boolean =
        next?.window?.first?.let { it <= today } ?: false

    /** ‹ is offered only while history reaches before this window; [earliest] is the first activity. */
    fun canStepBack(earliest: LocalDate?): Boolean {
        val start = window?.first ?: return false
        return earliest != null && earliest < start
    }

    /**
     * The `period` field of the insights request. The relative names are kept where they apply,
     * because the server's prompt phrases those ("this month (prior period: last month)"); any
     * other window goes as a readable label, which the server passes through as written.
     */
    fun wireName(today: LocalDate): String = when {
        this == thisMonth(today) -> "CURRENT_MONTH"
        this == lastMonth(today) -> "LAST_MONTH"
        this == thisYear(today) -> "CURRENT_YEAR"
        this == lastYear(today) -> "LAST_YEAR"
        this is Month -> "${month.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${month.year}"
        this is Year -> year.toString()
        this is Custom -> "$start to $endInclusive"
        else -> "ALL"
    }

    /** Coarse analytics bucket — the kind of window, never the dates themselves. */
    val kind: String
        get() = when (this) {
            is Month -> "month"
            is Year -> "year"
            is Custom -> "custom"
            All -> "all"
        }

    /** A stable string form, for nav keys (which carry strings). Inverse of [decode]. */
    fun encode(): String = when (this) {
        is Month -> "m:$month"
        is Year -> "y:$year"
        is Custom -> "c:$start:$endInclusive"
        All -> "all"
    }

    companion object {
        fun thisMonth(today: LocalDate): SummaryPeriod = Month(today.yearMonth)
        fun lastMonth(today: LocalDate): SummaryPeriod = Month(today.yearMonth.minus(1, DateTimeUnit.MONTH))
        fun thisYear(today: LocalDate): SummaryPeriod = Year(today.year)
        fun lastYear(today: LocalDate): SummaryPeriod = Year(today.year - 1)

        /** The [days] days ending today, today included. */
        fun lastDays(today: LocalDate, days: Int): SummaryPeriod =
            Custom(today.minus(days - 1, DateTimeUnit.DAY), today)

        /** This month and the eleven before it, whole months. */
        fun last12Months(today: LocalDate): SummaryPeriod =
            Custom(today.yearMonth.minus(11, DateTimeUnit.MONTH).firstDay, today)

        fun decode(value: String): SummaryPeriod? = runCatching {
            when {
                value == "all" -> All
                value.startsWith("m:") -> Month(YearMonth.parse(value.removePrefix("m:")))
                value.startsWith("y:") -> Year(value.removePrefix("y:").toInt())
                value.startsWith("c:") -> value.removePrefix("c:").split(":").let { (start, end) ->
                    Custom(LocalDate.parse(start), LocalDate.parse(end))
                }
                else -> null
            }
        }.getOrNull()
    }
}

/** How finely a chart over a window is bucketed — chosen by the window's length, not its kind. */
enum class Granularity { DAY, WEEK, MONTH, YEAR }

/**
 * Up to two months reads day by day; up to six, by week; up to three years, by month; beyond that
 * a month bar is too thin to read, so by year. A calendar month is therefore always daily and a
 * calendar year always monthly — the two views the dashboard had before custom windows.
 */
fun granularityFor(start: LocalDate, endExclusive: LocalDate): Granularity {
    val days = start.daysUntil(endExclusive)
    return when {
        days <= 62 -> Granularity.DAY
        days <= 186 -> Granularity.WEEK
        days <= 1096 -> Granularity.MONTH
        else -> Granularity.YEAR
    }
}

/** The bucket [date] falls in, identified by its first day. Weeks run from the window's [start]. */
fun bucketStart(date: LocalDate, start: LocalDate, granularity: Granularity): LocalDate = when (granularity) {
    Granularity.DAY -> date
    Granularity.WEEK -> start.plus((start.daysUntil(date) / 7) * 7, DateTimeUnit.DAY)
    Granularity.MONTH -> date.yearMonth.firstDay
    Granularity.YEAR -> LocalDate(date.year, 1, 1)
}

/**
 * Ordered bucket starts for [start, endExclusive), stopping at [today]: the part of a window that
 * hasn't happened yet has no bars.
 */
fun bucketStarts(
    start: LocalDate,
    endExclusive: LocalDate,
    today: LocalDate,
    granularity: Granularity,
): List<LocalDate> {
    val last = minOf(endExclusive.minus(1, DateTimeUnit.DAY), today)
    if (last < start) return emptyList()
    val out = mutableListOf<LocalDate>()
    var cursor = bucketStart(start, start, granularity)
    while (cursor <= last) {
        out += cursor
        cursor = bucketEnd(cursor, granularity)
    }
    return out
}

/** The bucket starting at [bucket] ends the day before this — its exclusive end. */
fun bucketEnd(bucket: LocalDate, granularity: Granularity): LocalDate = when (granularity) {
    Granularity.DAY -> bucket.plus(1, DateTimeUnit.DAY)
    Granularity.WEEK -> bucket.plus(7, DateTimeUnit.DAY)
    Granularity.MONTH -> bucket.plus(1, DateTimeUnit.MONTH)
    Granularity.YEAR -> bucket.plus(1, DateTimeUnit.YEAR)
}
