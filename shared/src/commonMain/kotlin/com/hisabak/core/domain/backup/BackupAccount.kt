package com.hisabak.core.domain.backup

import kotlinx.coroutines.flow.Flow

/** The Google account the user picked for Drive backup. */
data class BackupAccount(val email: String)

/** Remembers which account is connected for backup (the email only; tokens are fetched on demand). */
interface BackupAccountStore {
    val account: Flow<BackupAccount?>
    suspend fun set(account: BackupAccount)
    suspend fun clear()
}
