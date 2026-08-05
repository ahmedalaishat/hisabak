package com.hisabak.feature.transaction.domain

import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.core.common.Money
import kotlin.time.Instant

data class Transaction(
    val id: TransactionId,
    val amount: Money,
    val brandId: BrandId,
    val note: String? = null,
    val occurredAt: Instant,
    val sourceSmsId: String? = null,
)
