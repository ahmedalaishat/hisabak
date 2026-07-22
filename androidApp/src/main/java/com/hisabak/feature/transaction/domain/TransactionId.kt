package com.hisabak.feature.transaction.domain

import java.util.UUID

@JvmInline
value class TransactionId(val value: String) {
    companion object {
        fun new(): TransactionId = TransactionId(UUID.randomUUID().toString())
    }
}
