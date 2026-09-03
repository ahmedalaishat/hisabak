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
    /**
     * The merchant string as the message writes it, before canonicalization renamed it. Kept for
     * the same reason as [suggestedPattern] — it is only available at suggest time — and used on
     * confirm to learn the alias the synthesized template needs.
     */
    val suggestedBrandRaw: String? = null,
    /** The app saved this without asking. The row says so: the user did not approve it. */
    val autoConfirmed: Boolean = false,
    /**
     * The user has since looked at an [autoConfirmed] row. Kept separate from [autoConfirmed] so
     * that stays true forever — who created the row is history, whether anyone checked it is not.
     */
    val reviewed: Boolean = false,
) {
    val isLinked: Boolean get() = transactionId != null
}
