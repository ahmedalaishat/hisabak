package com.hisabak.core.data.backup

import android.content.Intent
import android.content.IntentSender
import com.hisabak.core.domain.backup.BackupAccount

/** Result of an authorization attempt for the Drive App Data scope. */
sealed interface AuthorizeOutcome {
    data class Granted(val account: BackupAccount, val accessToken: String) : AuthorizeOutcome

    /** The user must pick an account / grant consent — launch [intentSender] for a result. */
    data class NeedsConsent(val intentSender: IntentSender) : AuthorizeOutcome
    data object Failed : AuthorizeOutcome
}

/**
 * Authorizes Google Drive access for backup. Abstracted from the concrete Play Services
 * implementation so ViewModels and the remote can be unit-tested with a fake.
 */
interface DriveAuthorizer {
    suspend fun authorize(): AuthorizeOutcome
    fun resultFrom(data: Intent?): AuthorizeOutcome

    /** Silent access token for Drive calls; throws on auth required. */
    suspend fun accessToken(): String
}
