package com.hisabak.core.data.backup

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.hisabak.core.domain.backup.BackupAccount
import com.hisabak.core.domain.backup.BackupError
import com.hisabak.core.domain.backup.BackupException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Wraps the Google Identity **Authorization API** for the Drive App Data scope. It handles account
 * selection + consent and yields both the OAuth access token (for Drive REST) and the chosen
 * account's email. The interactive consent runs through a `PendingIntent` the caller (a Route)
 * launches for a result; silent [accessToken] is used for unattended Drive calls.
 */
class GoogleDriveAuthorizer(context: Context) : DriveAuthorizer {

    private val client = Identity.getAuthorizationClient(context)
    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
        .build()

    override suspend fun authorize(): AuthorizeOutcome = suspendCancellableCoroutine { cont ->
        client.authorize(request)
            .addOnSuccessListener { result -> cont.resume(result.toOutcome()) }
            .addOnFailureListener { e ->
                Log.w(TAG, "authorize() failed", e)
                cont.resume(AuthorizeOutcome.Failed)
            }
    }

    override fun resultFrom(data: Intent?): AuthorizeOutcome =
        runCatching { client.getAuthorizationResultFromIntent(data).toOutcome() }
            .getOrElse {
                Log.w(TAG, "resultFrom() failed", it)
                AuthorizeOutcome.Failed
            }

    override suspend fun accessToken(): String = when (val outcome = authorize()) {
        is AuthorizeOutcome.Granted -> outcome.accessToken
        else -> throw BackupException(BackupError.AuthRequired)
    }

    private fun AuthorizationResult.toOutcome(): AuthorizeOutcome {
        val resolution = pendingIntent
        if (hasResolution() && resolution != null) {
            return AuthorizeOutcome.NeedsConsent(resolution.intentSender)
        }
        val token = accessToken
        if (token == null) {
            Log.w(TAG, "Authorization succeeded but no access token was returned")
            return AuthorizeOutcome.Failed
        }
        // Only drive.appdata is requested, so the account email may be absent — that's fine, it
        // isn't shown. The access token is what backup needs.
        return AuthorizeOutcome.Granted(BackupAccount(toGoogleSignInAccount()?.email ?: ""), token)
    }

    private companion object {
        const val TAG = "HisabakBackup"
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }
}
