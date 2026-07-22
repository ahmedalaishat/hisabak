package com.hisabak.core.data.backup

import com.hisabak.core.domain.backup.BackupAccount

/** Opaque handle for a platform consent UI; the platform layer knows how to launch it. */
interface ConsentRequest

/** Opaque completion payload of a platform consent UI, fed back to [DriveAuthorizer.resultFrom]. */
interface ConsentResult

/** Result of an authorization attempt for the Drive App Data scope. */
sealed interface AuthorizeOutcome {
    data class Granted(val account: BackupAccount, val accessToken: String) : AuthorizeOutcome

    /** The user must pick an account / grant consent — launch [request] for a result. */
    data class NeedsConsent(val request: ConsentRequest) : AuthorizeOutcome

    /** Drive authorization isn't available on this platform (or in this build). */
    data object Unavailable : AuthorizeOutcome
    data object Failed : AuthorizeOutcome
}

/**
 * Authorizes Google Drive access for backup. Abstracted from the concrete platform
 * implementation so ViewModels and the remote can be unit-tested with a fake.
 */
interface DriveAuthorizer {
    suspend fun authorize(): AuthorizeOutcome
    fun resultFrom(result: ConsentResult?): AuthorizeOutcome

    /** Silent access token for Drive calls; throws on auth required. */
    suspend fun accessToken(): String
}
