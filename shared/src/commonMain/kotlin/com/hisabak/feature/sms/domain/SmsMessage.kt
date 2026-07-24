package com.hisabak.feature.sms.domain

import com.hisabak.feature.transaction.domain.TransactionId
import kotlin.time.Instant

data class SmsMessage(
    val id: SmsMessageId,
    val body: String,
    val receivedAt: Instant,
    val transactionId: TransactionId? = null,
    val parsed: ParsedSmsData? = null,
    /** Unconfirmed AI parse — shown as a suggestion until the user confirms (then it becomes [parsed]). */
    val suggested: ParsedSmsData? = null,
) {
    val isLinked: Boolean get() = transactionId != null
}
