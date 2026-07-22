package com.hisabak.di

import com.hisabak.BuildConfig
import com.hisabak.core.data.local.DatabaseSeeder
import com.hisabak.core.data.local.HisabakDatabase
import com.hisabak.core.data.local.hisabakDatabaseBuilder
import com.hisabak.core.data.local.security.DatabaseDecryptionMigration
import com.hisabak.core.data.local.security.KeystoreDatabaseKeyStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {
    single {
        // Versions up to 1.8.x encrypted this file with SQLCipher; databases carried over from
        // them are decrypted in place (once) before Room opens.
        DatabaseDecryptionMigration.migrateIfEncrypted(androidContext(), KeystoreDatabaseKeyStore(androidContext()))
        hisabakDatabaseBuilder(
            context = androidContext(),
            useDestructiveFallback = BuildConfig.DEBUG,
        ).build()
    }
    single { get<HisabakDatabase>().categoryDao() }
    single { get<HisabakDatabase>().categoryLimitDao() }
    single { get<HisabakDatabase>().brandDao() }
    single { get<HisabakDatabase>().transactionDao() }
    single { get<HisabakDatabase>().smsDao() }
    single { get<HisabakDatabase>().notificationDao() }
    single { get<HisabakDatabase>().categoryLimitAlertDao() }
    single { DatabaseSeeder(db = get(), seed = get(), starters = get(), currency = get()) }
}
