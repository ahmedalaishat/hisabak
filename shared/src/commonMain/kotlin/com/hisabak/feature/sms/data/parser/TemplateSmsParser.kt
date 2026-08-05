package com.hisabak.feature.sms.data.parser

import com.hisabak.core.common.Currency
import com.hisabak.core.common.Money
import com.hisabak.feature.sms.domain.ParsedSmsData
import com.hisabak.feature.sms.domain.SmsParser
import com.hisabak.feature.sms.domain.SmsTemplate
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toInstant

/**
 * Reads the field map produced by [RegexSmsTemplateDetector] and builds a [ParsedSmsData].
 * Handles amount normalization (strip thousands separators), multi-format date parsing,
 * and combines `date` + `time` placeholders into a single [Instant].
 */
class TemplateSmsParser(
    private val defaultCurrency: Currency,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) : SmsParser {

    override fun parse(body: String, template: SmsTemplate): ParsedSmsData {
        val fields = template.fields

        val amount = fields["amount"]?.let(::parseAmount)
        val brand = fields["brand"]?.trim()?.takeIf { it.isNotEmpty() }
        val occurredAt = parseDateTime(
            date = fields["date"] ?: fields["datetime"],
            time = fields["time"],
        )

        return ParsedSmsData(
            brandName = brand,
            amount = amount,
            occurredAt = occurredAt,
        )
    }

    // Bank SMS uses a fixed format: ',' is always a thousands separator, '.' the decimal point.
    private fun parseAmount(raw: String): Money? =
        Money.parseMajor(raw.replace(",", ""), defaultCurrency)

    private fun parseDateTime(date: String?, time: String?): Instant? {
        if (date.isNullOrBlank()) return null
        val normalised = date.replace('/', '-').trim()

        val parsedDate = DATE_FORMATS.firstNotNullOfOrNull { fmt ->
            runCatching { LocalDate.parse(normalised, fmt) }.getOrNull()
        } ?: return null

        val parsedTime = time?.trim()?.takeIf { it.isNotEmpty() }?.let { t ->
            TIME_FORMATS.firstNotNullOfOrNull { fmt ->
                runCatching { LocalTime.parse(t, fmt) }.getOrNull()
            }
        } ?: LocalTime(0, 0)

        return LocalDateTime(parsedDate, parsedTime).toInstant(zone)
    }

    private companion object {
        // Accepted bank-SMS date shapes: dd-MM-yyyy, d-M-yyyy, yyyy-MM-dd, dd-MM-yy, d-M-yy.
        // Two-digit years map to the 2000–2099 window.
        val DATE_FORMATS = listOf(
            LocalDate.Format {
                day(); char('-'); monthNumber(); char('-'); year()
            },
            LocalDate.Format {
                day(Padding.NONE); char('-'); monthNumber(Padding.NONE); char('-'); year()
            },
            LocalDate.Format {
                year(); char('-'); monthNumber(); char('-'); day()
            },
            LocalDate.Format {
                day(); char('-'); monthNumber(); char('-'); yearTwoDigits(baseYear = 2000)
            },
            LocalDate.Format {
                day(Padding.NONE); char('-'); monthNumber(Padding.NONE); char('-'); yearTwoDigits(baseYear = 2000)
            },
        )
        // Mirrors: HH:mm:ss, HH:mm, h:mm a, h:mma.
        val TIME_FORMATS = listOf(
            LocalTime.Format { hour(); char(':'); minute(); char(':'); second() },
            LocalTime.Format { hour(); char(':'); minute() },
            LocalTime.Format {
                amPmHour(Padding.NONE); char(':'); minute(); char(' '); amPmMarker("AM", "PM")
            },
            LocalTime.Format {
                amPmHour(Padding.NONE); char(':'); minute(); amPmMarker("AM", "PM")
            },
        )
    }
}
