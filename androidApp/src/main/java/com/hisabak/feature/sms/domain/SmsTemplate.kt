package com.hisabak.feature.sms.domain

data class SmsTemplate(
    val pattern: String,
    val fields: Map<String, String>,
) {
    init {
        require(pattern.isNotBlank()) { "SmsTemplate pattern must not be blank" }
    }
}
