package com.hisabak.core.data.local

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.hisabak.feature.brand.data.local.BrandDao
import com.hisabak.feature.brand.data.local.BrandEntity
import com.hisabak.feature.category.data.local.CategoryDao
import com.hisabak.feature.category.data.local.CategoryEntity
import com.hisabak.feature.category.data.local.CategoryLimitDao
import com.hisabak.feature.category.data.local.CategoryLimitEntity
import com.hisabak.feature.notification.data.local.CategoryLimitAlertDao
import com.hisabak.feature.notification.data.local.CategoryLimitAlertEntity
import com.hisabak.feature.notification.data.local.NotificationDao
import com.hisabak.feature.notification.data.local.NotificationEntity
import com.hisabak.feature.sms.data.local.SmsDao
import com.hisabak.feature.sms.data.local.SmsMessageEntity
import com.hisabak.feature.sms.data.local.SmsTemplateDao
import com.hisabak.feature.sms.data.local.SmsTemplateEntity
import com.hisabak.feature.transaction.data.local.TransactionDao
import com.hisabak.feature.transaction.data.local.TransactionEntity

@Database(
    entities = [
        CategoryEntity::class,
        CategoryLimitEntity::class,
        BrandEntity::class,
        TransactionEntity::class,
        SmsMessageEntity::class,
        SmsTemplateEntity::class,
        NotificationEntity::class,
        CategoryLimitAlertEntity::class,
    ],
    version = HisabakDatabase.SCHEMA_VERSION, // v7: autoConfirmed flag
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 2, to = 3, spec = DropSyncColumnsSpec::class),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
    ],
)
@ConstructedBy(HisabakDatabaseConstructor::class)
abstract class HisabakDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun categoryLimitDao(): CategoryLimitDao
    abstract fun brandDao(): BrandDao
    abstract fun transactionDao(): TransactionDao
    abstract fun smsDao(): SmsDao
    abstract fun smsTemplateDao(): SmsTemplateDao
    abstract fun notificationDao(): NotificationDao
    abstract fun categoryLimitAlertDao(): CategoryLimitAlertDao

    companion object {
        const val NAME = "hisabak.db"

        /** Single source of truth for the Room schema version; also stamped into backup files. */
        const val SCHEMA_VERSION = 7
    }
}

// The Room compiler generates the per-platform `actual` implementations.
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object HisabakDatabaseConstructor : RoomDatabaseConstructor<HisabakDatabase> {
    override fun initialize(): HisabakDatabase
}
