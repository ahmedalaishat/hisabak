package com.hisabak.di

import com.hisabak.core.common.AppConfig
import com.hisabak.core.data.backup.JsonBackupCodec
import com.hisabak.core.data.backup.RoomBackupRepository
import com.hisabak.core.data.local.HisabakDatabase
import com.hisabak.core.domain.backup.BackupCodec
import com.hisabak.core.domain.backup.BackupRepository
import com.hisabak.core.domain.backup.RestoreFromRemoteUseCase
import com.hisabak.core.domain.backup.RunBackupUseCase
import com.hisabak.feature.backup.presentation.BackupViewModel
import com.hisabak.feature.restore.presentation.RestoreViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Destination-agnostic backup engine + ViewModels. The platform pieces — passphrase store,
 *  account store, Drive authorizer/remote, crypto, scheduler — are bound per platform. */
val backupModule = module {
    single<BackupRepository> {
        RoomBackupRepository(
            db = get(),
            categoryDao = get(),
            categoryLimitDao = get(),
            brandDao = get(),
            transactionDao = get(),
            smsDao = get(),
        )
    }
    single<BackupCodec> { JsonBackupCodec() }

    factory {
        RunBackupUseCase(
            repository = get(),
            codec = get(),
            crypto = get(),
            remote = get(),
            clock = get(),
            appVersionCode = get<AppConfig>().versionCode,
            schemaVersion = HisabakDatabase.SCHEMA_VERSION,
        )
    }
    factory {
        RestoreFromRemoteUseCase(
            repository = get(),
            codec = get(),
            crypto = get(),
            remote = get(),
            schemaVersion = HisabakDatabase.SCHEMA_VERSION,
        )
    }

    viewModel {
        BackupViewModel(
            preferences = get(),
            passphraseStore = get(),
            accountStore = get(),
            authorizer = get(),
            runBackup = get(),
            remote = get(),
            scheduler = get(),
            clock = get(),
            analytics = get(),
        )
    }
    viewModel {
        RestoreViewModel(
            restoreFromRemote = get(),
            authorizer = get(),
            accountStore = get(),
            passphraseStore = get(),
            preferences = get(),
            analytics = get(),
        )
    }
}
