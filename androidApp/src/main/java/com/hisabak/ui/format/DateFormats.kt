package com.hisabak.ui.format

import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaZoneId

/**
 * Locale-aware date/time display formatting for Compose screens. Takes kotlinx types and formats
 * through java.time on Android — the only place outside data-layer boundaries allowed to touch
 * java.time. Becomes the `LocalizedDateFormatter` port when screens move to commonMain.
 */
fun formatLocalDate(date: LocalDate, pattern: String, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofPattern(pattern, locale).format(date.toJavaLocalDate())

fun formatInstant(
    instant: Instant,
    pattern: String,
    zone: TimeZone = TimeZone.currentSystemDefault(),
    locale: Locale = Locale.getDefault(),
): String =
    DateTimeFormatter.ofPattern(pattern, locale)
        .withZone(zone.toJavaZoneId())
        .format(instant.toJavaInstant())
