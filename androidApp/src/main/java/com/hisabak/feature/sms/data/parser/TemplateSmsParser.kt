package com.hisabak.feature.sms.data.parser

import com.hisabak.core.common.Currency
import com.hisabak.core.common.Money
import com.hisabak.feature.sms.domain.ParsedSmsData
import com.hisabak.feature.sms.domain.SmsParser
import com.hisabak.feature.sms.domain.SmsTemplate
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Reads the field map produced by [RegexSmsTemplateDetector] and builds a [ParsedSmsData].
 * Handles amount normalization (strip thousands separators), multi-format date parsing,
 * and combines `date` + `time` placeholders into a single [java.time.Instant].
 */
class TemplateSmsParser(
    private val defaultCurrency: Currency,
    private val zone: ZoneId = ZoneId.systemDefault(),
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

    private fun parseDateTime(date: String?, time: String?): java.time.Instant? {
        if (date.isNullOrBlank()) return null
        val normalised = date.replace('/', '-').trim()

        val parsedDate = DATE_FORMATS.firstNotNullOfOrNull { fmt ->
            runCatching { LocalDate.parse(normalised, fmt) }.getOrNull()
        } ?: return null

        val parsedTime = time?.trim()?.takeIf { it.isNotEmpty() }?.let { t ->
            TIME_FORMATS.firstNotNullOfOrNull { fmt ->
                runCatching { LocalTime.parse(t, fmt) }.getOrNull()
            }
        } ?: LocalTime.MIDNIGHT

        return LocalDateTime.of(parsedDate, parsedTime).atZone(zone).toInstant()
    }

    private companion object {
        val DATE_FORMATS = listOf(
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yy"),
            DateTimeFormatter.ofPattern("d-M-yy"),
        )
        val TIME_FORMATS = listOf(
            DateTimeFormatter.ofPattern("HH:mm:ss"),
            DateTimeFormatter.ofPattern("HH:mm"),
            DateTimeFormatter.ofPattern("h:mm a"),
            DateTimeFormatter.ofPattern("h:mma"),
        )
    }
}
