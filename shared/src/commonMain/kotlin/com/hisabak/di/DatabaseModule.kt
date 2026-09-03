package com.hisabak.di

import com.hisabak.core.data.local.DatabaseSeeder
import com.hisabak.core.data.local.HisabakDatabase
import org.koin.dsl.module

/** DAOs + seeder off the [HisabakDatabase] single that each platform module provides
 *  (androidApp runs the SQLCipher decryption migration first; iOS builds it directly). */
val databaseModule = module {
    single { get<HisabakDatabase>().categoryDao() }
    single { get<HisabakDatabase>().categoryLimitDao() }
    single { get<HisabakDatabase>().brandDao() }
    single { get<HisabakDatabase>().brandAliasDao() }
    single { get<HisabakDatabase>().transactionDao() }
    single { get<HisabakDatabase>().smsDao() }
    single { get<HisabakDatabase>().smsTemplateDao() }
    single { get<HisabakDatabase>().notificationDao() }
    single { get<HisabakDatabase>().categoryLimitAlertDao() }
    single { DatabaseSeeder(db = get(), seed = get(), starters = get(), currency = get()) }
}
