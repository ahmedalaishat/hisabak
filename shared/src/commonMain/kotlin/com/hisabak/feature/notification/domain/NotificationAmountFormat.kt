package com.hisabak.feature.notification.domain

import com.hisabak.core.common.Currency
import kotlin.math.abs

/** "AED 1,234.50"-style notification amount: currency code, comma grouping, exact cents.
 *  Hand-rolled (not `String.format`) so it's multiplatform-safe and locale-stable. */
internal fun formatNotificationAmount(currency: Currency, amountMinor: Long): String {
    val negative = amountMinor < 0
    val abs = abs(amountMinor)
    val units = (abs / 100).toString().reversed().chunked(3).joinToString(",").reversed()
    val cents = (abs % 100).toString().padStart(2, '0')
    return "${currency.code} " + (if (negative) "-" else "") + "$units.$cents"
}
