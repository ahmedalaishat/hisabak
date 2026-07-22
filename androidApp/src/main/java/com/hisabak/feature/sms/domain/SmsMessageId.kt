package com.hisabak.feature.sms.domain

import java.util.UUID

@JvmInline
value class SmsMessageId(val value: String) {
    companion object {
        fun new(): SmsMessageId = SmsMessageId(UUID.randomUUID().toString())
    }
}
