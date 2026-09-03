package com.hisabak.core.data.local

import androidx.room.DeleteColumn
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v1 → v2: adds the notifications and category_limit_alerts tables for budget-limit alerts.
 * Purely additive, so existing data (categories, brands, transactions, limits) is preserved.
 * The DDL mirrors what Room generates for [com.hisabak.feature.notification.data.local]
 * entities; keep it in sync if those change.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `notifications` (" +
                "`id` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`message` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`categoryId` TEXT, " +
                "`createdAtMillis` INTEGER NOT NULL, " +
                "`isRead` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_notifications_createdAtMillis` " +
                "ON `notifications` (`createdAtMillis`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_notifications_isRead` " +
                "ON `notifications` (`isRead`)",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `category_limit_alerts` (" +
                "`categoryId` TEXT NOT NULL, " +
                "`periodMonth` INTEGER NOT NULL, " +
                "`lastLevel` INTEGER NOT NULL, " +
                "PRIMARY KEY(`categoryId`, `periodMonth`))",
        )
    }
}

/**
 * v2 → v3: drops the unused sync-metadata columns (`updatedAtMillis`, `isDirty`, `deletedAtMillis`,
 * `serverId`, `version`) that every financial table carried for a cloud sync that was never built.
 * Registered as an AutoMigration in [HisabakDatabase] so Room generates the table rebuilds
 * (SQLite on minSdk 29 has no DROP COLUMN).
 */
@DeleteColumn(tableName = "categories", columnName = "updatedAtMillis")
@DeleteColumn(tableName = "categories", columnName = "isDirty")
@DeleteColumn(tableName = "categories", columnName = "deletedAtMillis")
@DeleteColumn(tableName = "categories", columnName = "serverId")
@DeleteColumn(tableName = "categories", columnName = "version")
@DeleteColumn(tableName = "brands", columnName = "updatedAtMillis")
@DeleteColumn(tableName = "brands", columnName = "isDirty")
@DeleteColumn(tableName = "brands", columnName = "deletedAtMillis")
@DeleteColumn(tableName = "brands", columnName = "serverId")
@DeleteColumn(tableName = "brands", columnName = "version")
@DeleteColumn(tableName = "transactions", columnName = "updatedAtMillis")
@DeleteColumn(tableName = "transactions", columnName = "isDirty")
@DeleteColumn(tableName = "transactions", columnName = "deletedAtMillis")
@DeleteColumn(tableName = "transactions", columnName = "serverId")
@DeleteColumn(tableName = "transactions", columnName = "version")
@DeleteColumn(tableName = "sms_messages", columnName = "updatedAtMillis")
@DeleteColumn(tableName = "sms_messages", columnName = "isDirty")
@DeleteColumn(tableName = "sms_messages", columnName = "deletedAtMillis")
@DeleteColumn(tableName = "sms_messages", columnName = "serverId")
@DeleteColumn(tableName = "sms_messages", columnName = "version")
class DropSyncColumnsSpec : AutoMigrationSpec

/**
 * v10 → v11: `insight_narratives` is re-keyed from the period name to the digest of what was
 * explained. It is a cache — rows are regenerated on the next tap — so the table is rebuilt empty
 * rather than migrated. Manual because Room's AutoMigration cannot change a primary key.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `insight_narratives`")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `insight_narratives` (" +
                "`narrativeKey` TEXT NOT NULL, " +
                "`payload` TEXT NOT NULL, " +
                "`createdAtMillis` INTEGER NOT NULL, " +
                "PRIMARY KEY(`narrativeKey`))",
        )
    }
}
