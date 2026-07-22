package com.hisabak.feature.transaction.domain

import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@JvmInline
value class TransactionId(val value: String) {
    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): TransactionId = TransactionId(Uuid.random().toString())
    }
}
