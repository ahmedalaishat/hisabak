package com.hisabak.feature.sms.domain

fun interface SmsParser {
    fun parse(body: String, template: SmsTemplate): ParsedSmsData
}
