package com.hisabak.core.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Android builder for [HisabakDatabase]. Uses the framework SQLite driver (no [setDriver]),
 * exactly like the pre-KMP configuration, so the `DatabaseDecryptionMigration` pre-open hook and
 * on-device behavior are unchanged.
 */
fun hisabakDatabaseBuilder(
    context: Context,
    useDestructiveFallback: Boolean,
): RoomDatabase.Builder<HisabakDatabase> =
    Room.databaseBuilder<HisabakDatabase>(
        context = context,
        name = HisabakDatabase.NAME,
    )
        .addMigrations(MIGRATION_1_2, MIGRATION_10_11)
        // Destructive fallback only in debug builds, for fast schema iteration. Release
        // builds must ship a real migration — a missing one fails loudly rather than
        // silently wiping the user's on-device financial history.
        .apply { if (useDestructiveFallback) fallbackToDestructiveMigration(dropAllTables = true) }
