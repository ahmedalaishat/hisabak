package com.hisabak.ui.format

import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime

/**
 * Locale-aware date/time + file-size display formatting for shared screens — the port that
 * replaced the java.time-based `ui/format/DateFormats.kt`. The Android implementation formats
 * through java.time/DateUtils; the composition default is the pure-kotlinx
 * [BasicLocalizedDateFormatter]. Methods cover exactly the formats the screens use.
 */
interface LocalizedDateFormatter {
    /** Weekday + full date — "Wed, Jul 22, 2026" (transaction edit date field). */
    fun weekdayDate(date: LocalDate): String

    /** Month + day — "Jul 22" (same-year transaction day headers). */
    fun monthDay(date: LocalDate): String

    /** Full date — "Jul 22, 2026" (other-year day headers, transaction rows). */
    fun fullDate(date: LocalDate): String

    fun fullDate(instant: Instant): String

    /** Full date + 24h time — "Jul 22, 2026 14:05" (SMS timestamps). */
    fun dateTime(instant: Instant): String

    /** Day + month — "22 Jul" (notification times, daily chart labels). */
    fun dayMonth(date: LocalDate): String

    fun dayMonth(instant: Instant): String

    /** Short month — "Jul" (monthly chart labels). */
    fun month(date: LocalDate): String

    /** Short month + 2-digit year — "Jul '26" (multi-year chart labels). */
    fun monthYear(date: LocalDate): String

    /** Relative "5 minutes ago"-style timestamp for the last backup. */
    fun relativeDateTime(epochMillis: Long): String

    /** Short human-readable file size — "12 kB". */
    fun shortFileSize(bytes: Long): String
}

/** Ambient formatter; the app root provides the platform implementation. */
val LocalDateFormatter = staticCompositionLocalOf<LocalizedDateFormatter> {
    BasicLocalizedDateFormatter()
}

/**
 * Pure-kotlinx fallback: fixed English month/day names, Western digits, no relative phrasing.
 * Doubles as the iOS behavior until a real NSDateFormatter-backed implementation lands.
 * TODO(Phase-B): replace with a locale-aware iOS implementation.
 */
class BasicLocalizedDateFormatter : LocalizedDateFormatter {

    private val weekdayDate = LocalDate.Format {
        dayOfWeek(DayOfWeekNames.ENGLISH_ABBREVIATED); chars(", ")
        monthName(MonthNames.ENGLISH_ABBREVIATED); char(' '); day(Padding.NONE); chars(", "); year()
    }
    private val monthDay = LocalDate.Format {
        monthName(MonthNames.ENGLISH_ABBREVIATED); char(' '); day(Padding.NONE)
    }
    private val fullDate = LocalDate.Format {
        monthName(MonthNames.ENGLISH_ABBREVIATED); char(' '); day(Padding.NONE); chars(", "); year()
    }
    private val dayMonth = LocalDate.Format {
        day(Padding.NONE); char(' '); monthName(MonthNames.ENGLISH_ABBREVIATED)
    }
    private val month = LocalDate.Format { monthName(MonthNames.ENGLISH_ABBREVIATED) }

    private fun localDate(instant: Instant): LocalDate =
        instant.toLocalDateTime(TimeZone.currentSystemDefault()).date

    override fun weekdayDate(date: LocalDate): String = date.format(weekdayDate)
    override fun monthDay(date: LocalDate): String = date.format(monthDay)
    override fun fullDate(date: LocalDate): String = date.format(fullDate)
    override fun fullDate(instant: Instant): String = fullDate(localDate(instant))

    override fun dateTime(instant: Instant): String {
        val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val hh = local.hour.toString().padStart(2, '0')
        val mm = local.minute.toString().padStart(2, '0')
        return "${fullDate(local.date)} $hh:$mm"
    }

    override fun dayMonth(date: LocalDate): String = date.format(dayMonth)
    override fun dayMonth(instant: Instant): String = dayMonth(localDate(instant))
    override fun month(date: LocalDate): String = date.format(month)
    override fun monthYear(date: LocalDate): String =
        "${month(date)} '${(date.year % 100).toString().padStart(2, '0')}"

    override fun relativeDateTime(epochMillis: Long): String =
        dateTime(Instant.fromEpochMilliseconds(epochMillis))

    override fun shortFileSize(bytes: Long): String = when {
        bytes < 1000 -> "$bytes B"
        bytes < 1000_000 -> "${roundUnit(bytes, 1000.0)} kB"
        bytes < 1000_000_000 -> "${roundUnit(bytes, 1000_000.0)} MB"
        else -> "${roundUnit(bytes, 1000_000_000.0)} GB"
    }

    private fun roundUnit(bytes: Long, unit: Double): String {
        val value = bytes / unit
        val rounded = (value * 100).toLong() / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }
}
