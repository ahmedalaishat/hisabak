package com.hisabak.core.domain.backup

import kotlinx.serialization.Serializable

/**
 * A logical snapshot of the user's financial data — the wire format for backups, decoupled from the
 * Room entities so the schema can evolve independently. Records mirror the financial tables
 * plus the SMS parse templates;
 * added fields use defaults so older backups decode forward.
 * Excludes ephemeral/derived tables (notifications, category limit alerts) and app settings.
 */
@Serializable
data class BackupData(
    val categories: List<CategoryRecord> = emptyList(),
    val categoryLimits: List<CategoryLimitRecord> = emptyList(),
    val brands: List<BrandRecord> = emptyList(),
    val brandAliases: List<BrandAliasRecord> = emptyList(),
    val transactions: List<TransactionRecord> = emptyList(),
    val smsMessages: List<SmsMessageRecord> = emptyList(),
    val smsTemplates: List<SmsTemplateRecord> = emptyList(),
) {
    /** User-data record count — the "nothing to back up" gate. Templates are excluded: the 10
     *  seeded defaults always exist, and counting them would make an empty install look
     *  backup-worthy. */
    val totalRecords: Int
        get() = categories.size + categoryLimits.size + brands.size + transactions.size +
            smsMessages.size
}

/**
 * A learned merchant-string → brand mapping. Backed up unlike the AI provenance flags: those
 * describe one device's history, this is knowledge the user's other devices need to avoid
 * re-creating duplicate brands from the same bank formats.
 */
@Serializable
data class BrandAliasRecord(
    val alias: String,
    val brandId: String,
)

@Serializable
data class CategoryRecord(
    val id: String,
    val name: String,
    val type: String,
    val color: String,
    val icon: String,
)

@Serializable
data class CategoryLimitRecord(
    val categoryId: String,
    val effectiveFrom: Int,
    val amountMinor: Long? = null,
    val currency: String,
)

@Serializable
data class BrandRecord(
    val id: String,
    val name: String,
    val categoryId: String? = null,
)

@Serializable
data class TransactionRecord(
    val id: String,
    val amountMinor: Long,
    val currency: String,
    val brandId: String,
    val note: String? = null,
    val occurredAtMillis: Long,
    val sourceSmsId: String? = null,
)

@Serializable
data class SmsTemplateRecord(
    val id: String,
    val pattern: String,
    val sampleBody: String? = null,
    val isDefault: Boolean,
    val enabled: Boolean = true,
    val createdAtMillis: Long,
    val derivedByAi: Boolean = false,
)

@Serializable
data class SmsMessageRecord(
    val id: String,
    val body: String,
    val receivedAtMillis: Long,
    val transactionId: String? = null,
    val parsedBrandName: String? = null,
    val parsedAmountMinor: Long? = null,
    val parsedCurrency: String? = null,
    val parsedOccurredAtMillis: Long? = null,
)
