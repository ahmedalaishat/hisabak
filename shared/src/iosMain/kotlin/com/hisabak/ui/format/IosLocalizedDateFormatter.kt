package com.hisabak.ui.format

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import platform.Foundation.NSByteCountFormatter
import platform.Foundation.NSByteCountFormatterCountStyleFile
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSRelativeDateTimeFormatter
import platform.Foundation.NSRelativeDateTimeFormatterUnitsStyleFull
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970

/**
 * NSDateFormatter-backed [LocalizedDateFormatter]: localized month/weekday names and pattern
 * order from templates, relative phrasing from NSRelativeDateTimeFormatter, file sizes from
 * NSByteCountFormatter — the iOS counterpart of `AndroidLocalizedDateFormatter`.
 */
class IosLocalizedDateFormatter : LocalizedDateFormatter {

    private fun template(skeleton: String): NSDateFormatter = NSDateFormatter().apply {
        locale = NSLocale.currentLocale
        setLocalizedDateFormatFromTemplate(skeleton)
    }

    private val weekdayDate = template("EEEMMMdy")
    private val monthDay = template("MMMd")
    private val fullDate = template("MMMdy")
    private val dateTime = template("MMMdyHHmm")
    private val dayMonth = template("dMMM")
    private val month = template("MMM")
    private val monthYear = template("MMMyy")

    private val relative = NSRelativeDateTimeFormatter().apply {
        locale = NSLocale.currentLocale
        unitsStyle = NSRelativeDateTimeFormatterUnitsStyleFull
    }

    private val byteCount = NSByteCountFormatter().apply {
        countStyle = NSByteCountFormatterCountStyleFile
    }

    private fun LocalDate.toNSDate(): NSDate = Instant
        .fromEpochMilliseconds(atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds())
        .toNSDate()

    private fun Instant.toNSDate(): NSDate =
        NSDate.dateWithTimeIntervalSince1970(toEpochMilliseconds() / 1000.0)

    override fun weekdayDate(date: LocalDate): String = weekdayDate.stringFromDate(date.toNSDate())
    override fun monthDay(date: LocalDate): String = monthDay.stringFromDate(date.toNSDate())
    override fun fullDate(date: LocalDate): String = fullDate.stringFromDate(date.toNSDate())
    override fun fullDate(instant: Instant): String = fullDate.stringFromDate(instant.toNSDate())
    override fun dateTime(instant: Instant): String = dateTime.stringFromDate(instant.toNSDate())
    override fun dayMonth(date: LocalDate): String = dayMonth.stringFromDate(date.toNSDate())
    override fun dayMonth(instant: Instant): String = dayMonth.stringFromDate(instant.toNSDate())
    override fun month(date: LocalDate): String = month.stringFromDate(date.toNSDate())
    override fun monthYear(date: LocalDate): String = monthYear.stringFromDate(date.toNSDate())

    override fun relativeDateTime(epochMillis: Long): String =
        relative.localizedStringForDate(
            Instant.fromEpochMilliseconds(epochMillis).toNSDate(),
            relativeToDate = NSDate(),
        )

    override fun shortFileSize(bytes: Long): String = byteCount.stringFromByteCount(bytes)
}
