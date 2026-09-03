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
    /** Template pattern derived from [suggested] at parse time, installed if the user confirms. */
    val suggestedPattern: String? = null,
    /** The app saved this without asking. The row says so: the user did not approve it. */
    val autoConfirmed: Boolean = false,
) {
    val isLinked: Boolean get() = transactionId != null
}
