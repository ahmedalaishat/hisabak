package com.hisabak.feature.sms.domain

import com.hisabak.core.common.Money
import java.time.Instant

data class ParsedSmsData(
    val brandName: String?,
    val amount: Money?,
    val occurredAt: Instant?,
) {
    val isComplete: Boolean get() = brandName != null && amount != null
}
