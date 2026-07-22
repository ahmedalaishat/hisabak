package com.hisabak.feature.sms.domain

import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@JvmInline
value class SmsMessageId(val value: String) {
    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): SmsMessageId = SmsMessageId(Uuid.random().toString())
    }
}
