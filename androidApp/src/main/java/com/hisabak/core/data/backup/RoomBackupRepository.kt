package com.hisabak.core.data.backup

import androidx.room.withTransaction
import com.hisabak.core.data.local.HisabakDatabase
import com.hisabak.core.domain.backup.BackupData
import com.hisabak.core.domain.backup.BackupRepository
import com.hisabak.core.domain.backup.BrandRecord
import com.hisabak.core.domain.backup.CategoryLimitRecord
import com.hisabak.core.domain.backup.CategoryRecord
import com.hisabak.core.domain.backup.SmsMessageRecord
import com.hisabak.core.domain.backup.TransactionRecord
import com.hisabak.feature.brand.data.local.BrandDao
import com.hisabak.feature.brand.data.local.BrandEntity
import com.hisabak.feature.category.data.local.CategoryDao
import com.hisabak.feature.category.data.local.CategoryEntity
import com.hisabak.feature.category.data.local.CategoryLimitDao
import com.hisabak.feature.category.data.local.CategoryLimitEntity
import com.hisabak.feature.sms.data.local.SmsDao
import com.hisabak.feature.sms.data.local.SmsMessageEntity
import com.hisabak.feature.transaction.data.local.TransactionDao
import com.hisabak.feature.transaction.data.local.TransactionEntity

class RoomBackupRepository(
    private val db: HisabakDatabase,
    private val categoryDao: CategoryDao,
    private val categoryLimitDao: CategoryLimitDao,
    private val brandDao: BrandDao,
    private val transactionDao: TransactionDao,
    private val smsDao: SmsDao,
) : BackupRepository {

    override suspend fun snapshot(): BackupData = BackupData(
        categories = categoryDao.getAllForBackup().map { it.toRecord() },
        categoryLimits = categoryLimitDao.getAllForBackup().map { it.toRecord() },
        brands = brandDao.getAllForBackup().map { it.toRecord() },
        transactions = transactionDao.getAllForBackup().map { it.toRecord() },
        smsMessages = smsDao.getAllForBackup().map { it.toRecord() },
    )

    override suspend fun replaceAll(data: BackupData) = db.withTransaction {
        // Delete children → parents to respect the brand/transaction foreign keys.
        transactionDao.deleteAll()
        smsDao.deleteAll()
        categoryLimitDao.deleteAll()
        brandDao.deleteAll()
        categoryDao.deleteAll()
        // Insert parents → children.
        categoryDao.upsertAll(data.categories.map { it.toEntity() })
        brandDao.upsertAll(data.brands.map { it.toEntity() })
        transactionDao.upsertAll(data.transactions.map { it.toEntity() })
        smsDao.upsertAll(data.smsMessages.map { it.toEntity() })
        categoryLimitDao.upsertAll(data.categoryLimits.map { it.toEntity() })
    }
}

private fun CategoryEntity.toRecord() = CategoryRecord(id, name, type, color, icon)

private fun CategoryRecord.toEntity() = CategoryEntity(id, name, type, color, icon)

private fun CategoryLimitEntity.toRecord() = CategoryLimitRecord(categoryId, effectiveFrom, amountMinor, currency)

private fun CategoryLimitRecord.toEntity() = CategoryLimitEntity(categoryId, effectiveFrom, amountMinor, currency)

private fun BrandEntity.toRecord() = BrandRecord(id, name, categoryId)

private fun BrandRecord.toEntity() = BrandEntity(id, name, categoryId)

private fun TransactionEntity.toRecord() = TransactionRecord(
    id, amountMinor, currency, brandId, note, occurredAtMillis, sourceSmsId,
)

private fun TransactionRecord.toEntity() = TransactionEntity(
    id, amountMinor, currency, brandId, note, occurredAtMillis, sourceSmsId,
)

private fun SmsMessageEntity.toRecord() = SmsMessageRecord(
    id, body, receivedAtMillis, transactionId, parsedBrandName, parsedAmountMinor, parsedCurrency,
    parsedOccurredAtMillis,
)

private fun SmsMessageRecord.toEntity() = SmsMessageEntity(
    id, body, receivedAtMillis, transactionId, parsedBrandName, parsedAmountMinor, parsedCurrency,
    parsedOccurredAtMillis,
)
