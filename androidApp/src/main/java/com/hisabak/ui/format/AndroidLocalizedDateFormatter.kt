package com.hisabak.ui.format

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaZoneId

/**
 * Android implementation of the [LocalizedDateFormatter] port. Takes kotlinx types and formats
 * through java.time / DateUtils / Formatter — the only place outside data-layer boundaries
 * allowed to touch java.time. Provided at the app root via [LocalDateFormatter].
 */
class AndroidLocalizedDateFormatter(private val context: Context) : LocalizedDateFormatter {

    override fun weekdayDate(date: LocalDate): String = formatLocalDate(date, "EEE, MMM d, yyyy")
    override fun monthDay(date: LocalDate): String = formatLocalDate(date, "MMM d")
    override fun fullDate(date: LocalDate): String = formatLocalDate(date, "MMM d, yyyy")
    override fun fullDate(instant: Instant): String = formatInstant(instant, "MMM d, yyyy")
    override fun dateTime(instant: Instant): String = formatInstant(instant, "MMM d, yyyy HH:mm")
    override fun dayMonth(date: LocalDate): String = formatLocalDate(date, "d MMM")
    override fun dayMonth(instant: Instant): String = formatInstant(instant, "d MMM")
    override fun month(date: LocalDate): String = formatLocalDate(date, "MMM")
    override fun monthYear(date: LocalDate): String = formatLocalDate(date, "MMM ''yy")

    override fun relativeDateTime(epochMillis: Long): String =
        DateUtils.getRelativeDateTimeString(
            context, epochMillis,
            DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0,
        ).toString()

    override fun shortFileSize(bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

    private fun formatLocalDate(date: LocalDate, pattern: String): String =
        DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(date.toJavaLocalDate())

    private fun formatInstant(instant: Instant, pattern: String): String =
        DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
            .withZone(TimeZone.currentSystemDefault().toJavaZoneId())
            .format(instant.toJavaInstant())
}
