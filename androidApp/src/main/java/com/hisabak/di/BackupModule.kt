package com.hisabak.di

import com.hisabak.BuildConfig
import com.hisabak.core.data.backup.AesGcmBackupCrypto
import com.hisabak.core.data.backup.DataStoreBackupAccountStore
import com.hisabak.core.data.backup.DriveAuthorizer
import com.hisabak.core.data.backup.GoogleDriveAuthorizer
import com.hisabak.core.data.backup.GoogleDriveBackupRemote
import com.hisabak.core.data.backup.JsonBackupCodec
import com.hisabak.core.data.backup.KeystoreBackupPassphraseStore
import com.hisabak.core.data.backup.RoomBackupRepository
import com.hisabak.core.data.backup.WorkManagerAutoBackupScheduler
import com.hisabak.core.data.local.HisabakDatabase
import com.hisabak.core.domain.backup.AutoBackupScheduler
import com.hisabak.core.domain.backup.BackupAccountStore
import com.hisabak.core.domain.backup.BackupCodec
import com.hisabak.core.domain.backup.BackupCrypto
import com.hisabak.core.domain.backup.BackupPassphraseStore
import com.hisabak.core.domain.backup.BackupRemote
import com.hisabak.core.domain.backup.BackupRepository
import com.hisabak.core.domain.backup.RestoreFromRemoteUseCase
import com.hisabak.core.domain.backup.RunBackupUseCase
import com.hisabak.feature.backup.presentation.BackupViewModel
import com.hisabak.feature.restore.presentation.RestoreViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val backupModule = module {
    single<BackupPassphraseStore> { KeystoreBackupPassphraseStore(androidContext()) }
    single<BackupAccountStore> { DataStoreBackupAccountStore(androidContext()) }
    single<DriveAuthorizer> { GoogleDriveAuthorizer(androidContext()) }
    single<BackupRemote> { GoogleDriveBackupRemote(authorizer = get()) }
    single<AutoBackupScheduler> { WorkManagerAutoBackupScheduler(androidContext()) }

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
    single<BackupCrypto> { AesGcmBackupCrypto() }

    factory {
        RunBackupUseCase(
            repository = get(),
            codec = get(),
            crypto = get(),
            remote = get(),
            clock = get(),
            appVersionCode = BuildConfig.VERSION_CODE,
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
