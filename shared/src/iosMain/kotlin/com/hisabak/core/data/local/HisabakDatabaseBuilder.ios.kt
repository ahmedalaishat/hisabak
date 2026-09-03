package com.hisabak.core.data.local

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** iOS builder for [HisabakDatabase]: bundled SQLite driver, database file in Documents. */
fun hisabakDatabaseBuilder(): RoomDatabase.Builder<HisabakDatabase> =
    Room.databaseBuilder<HisabakDatabase>(
        name = "${documentDirectoryPath()}/${HisabakDatabase.NAME}",
    )
        .addMigrations(MIGRATION_1_2, MIGRATION_10_11)
        .setDriver(BundledSQLiteDriver())

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectoryPath(): String {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(documentDirectory?.path)
}
