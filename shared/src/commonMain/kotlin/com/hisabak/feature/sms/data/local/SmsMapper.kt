package com.hisabak.feature.sms.data.local

import com.hisabak.core.common.Currency
import com.hisabak.core.common.Money
import com.hisabak.feature.sms.domain.ParsedSmsData
import com.hisabak.feature.sms.domain.SmsMessage
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.feature.transaction.domain.TransactionId
import kotlin.time.Instant

fun SmsMessageEntity.toDomain(): SmsMessage {
    val parsed = if (parsedBrandName != null || parsedAmountMinor != null || parsedOccurredAtMillis != null) {
        ParsedSmsData(
            brandName = parsedBrandName,
            amount = parsedAmountMinor?.let { Money(it, Currency(parsedCurrency ?: "AED")) },
            occurredAt = parsedOccurredAtMillis?.let(Instant::fromEpochMilliseconds),
        )
    } else null

    val suggested = if (suggestedBrandName != null || suggestedAmountMinor != null || suggestedOccurredAtMillis != null) {
        ParsedSmsData(
            brandName = suggestedBrandName,
            amount = suggestedAmountMinor?.let { Money(it, Currency(suggestedCurrency ?: "AED")) },
            occurredAt = suggestedOccurredAtMillis?.let(Instant::fromEpochMilliseconds),
        )
    } else null

    return SmsMessage(
        id = SmsMessageId(id),
        body = body,
        receivedAt = Instant.fromEpochMilliseconds(receivedAtMillis),
        transactionId = transactionId?.let(::TransactionId),
        parsed = parsed,
        suggested = suggested,
    )
}

fun SmsMessage.toEntity(): SmsMessageEntity = SmsMessageEntity(
    id = id.value,
    body = body,
    receivedAtMillis = receivedAt.toEpochMilliseconds(),
    transactionId = transactionId?.value,
    parsedBrandName = parsed?.brandName,
    parsedAmountMinor = parsed?.amount?.amountMinor,
    parsedCurrency = parsed?.amount?.currency?.code,
    parsedOccurredAtMillis = parsed?.occurredAt?.toEpochMilliseconds(),
    suggestedBrandName = suggested?.brandName,
    suggestedAmountMinor = suggested?.amount?.amountMinor,
    suggestedCurrency = suggested?.amount?.currency?.code,
    suggestedOccurredAtMillis = suggested?.occurredAt?.toEpochMilliseconds(),
)
