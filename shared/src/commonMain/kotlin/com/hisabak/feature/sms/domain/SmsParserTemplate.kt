package com.hisabak.feature.sms.domain

import kotlin.time.Instant

/** A stored parse template. Defaults ship with the app (disable-only); user templates are
 *  defined by tagging a sample message and keep that sample for re-editing. */
data class SmsParserTemplate(
    val id: SmsTemplateId,
    val pattern: String,
    val sampleBody: String?,
    val isDefault: Boolean,
    val enabled: Boolean,
    val createdAt: Instant,
)

data class SmsTemplateId(val value: String) {
    companion object {
        @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
        fun new(): SmsTemplateId = SmsTemplateId(kotlin.uuid.Uuid.random().toString())
    }
}
