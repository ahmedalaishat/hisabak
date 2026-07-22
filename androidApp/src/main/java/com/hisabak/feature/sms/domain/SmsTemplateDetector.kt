package com.hisabak.feature.sms.domain

fun interface SmsTemplateDetector {
    fun detect(body: String): SmsTemplate?
}
