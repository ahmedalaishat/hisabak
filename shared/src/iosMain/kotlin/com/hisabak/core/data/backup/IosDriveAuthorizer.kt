package com.hisabak.core.data.backup

import com.hisabak.core.domain.backup.BackupAccount
import com.hisabak.core.domain.backup.BackupError
import com.hisabak.core.domain.backup.BackupException
import kotlin.coroutines.resume
import kotlin.time.Clock
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSBundle
import platform.Foundation.NSCharacterSet
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSURLResponse
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.Foundation.stringByAddingPercentEncodingWithAllowedCharacters
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault
import platform.UIKit.UIApplication
import platform.darwin.NSObject as DarwinNSObject

/**
 * Google OAuth for the Drive App Data scope via ASWebAuthenticationSession + PKCE — the iOS
 * counterpart of androidApp's Play-Services authorizer. The interactive consent runs inline in
 * [authorize] (no NeedsConsent hop: the web sheet IS the consent UI), the refresh token lives in
 * the Keychain, and [accessToken] refreshes silently.
 *
 * Requires an **iOS OAuth client** in the app's GCP project: put its client id in Info.plist
 * under `GoogleOAuthClientID`. Absent → [AuthorizeOutcome.Unavailable] (the pre-B4 behavior).
 */
@OptIn(ExperimentalForeignApi::class)
class IosDriveAuthorizer : DriveAuthorizer {

    private val json = Json { ignoreUnknownKeys = true }
    private val refreshTokenStore = IosKeychainBackupPassphraseStore(account = "drive_refresh_token")
    private val mutex = Mutex()
    private var cachedToken: String? = null
    private var cachedTokenExpiresAtMillis: Long = 0

    private val clientId: String? =
        (NSBundle.mainBundle.objectForInfoDictionaryKey("GoogleOAuthClientID") as? String)
            ?.takeIf { it.isNotBlank() }

    override suspend fun authorize(): AuthorizeOutcome {
        val client = clientId ?: return AuthorizeOutcome.Unavailable
        // Prefer a silent path when a refresh token already exists.
        runCatching { return AuthorizeOutcome.Granted(BackupAccount(""), accessToken()) }
        val verifier = codeVerifier()
        val code = runCatching { authorizationCode(client, verifier) }.getOrNull()
            ?: return AuthorizeOutcome.Failed
        val tokens = runCatching { exchangeCode(client, code, verifier) }.getOrNull()
            ?: return AuthorizeOutcome.Failed
        tokens.refreshToken?.let { refreshTokenStore.set(it) }
        cache(tokens)
        return AuthorizeOutcome.Granted(BackupAccount(""), tokens.accessToken)
    }

    override fun resultFrom(result: ConsentResult?): AuthorizeOutcome =
        AuthorizeOutcome.Failed // iOS consent completes inline in authorize(); nothing arrives here.

    override suspend fun accessToken(): String = mutex.withLock {
        val now = Clock.System.now().toEpochMilliseconds()
        cachedToken?.takeIf { now < cachedTokenExpiresAtMillis - EXPIRY_MARGIN_MILLIS }?.let { return it }
        val client = clientId ?: throw BackupException(BackupError.AuthRequired)
        val refresh = refreshTokenStore.get() ?: throw BackupException(BackupError.AuthRequired)
        val tokens = runCatching { refreshAccessToken(client, refresh) }.getOrElse {
            throw BackupException(BackupError.AuthRequired)
        }
        cache(tokens)
        tokens.accessToken
    }

    private fun cache(tokens: TokenDto) {
        cachedToken = tokens.accessToken
        cachedTokenExpiresAtMillis =
            Clock.System.now().toEpochMilliseconds() + tokens.expiresIn * 1000L
    }

    /** Runs the ASWebAuthenticationSession sheet and returns the authorization code. */
    private suspend fun authorizationCode(client: String, verifier: String): String =
        withContext(Dispatchers.Main) {
            val scheme = reversedClientScheme(client)
            val authUrl = "https://accounts.google.com/o/oauth2/v2/auth" +
                "?client_id=${client.percentEncoded()}" +
                "&redirect_uri=${"$scheme:/oauth2redirect".percentEncoded()}" +
                "&response_type=code" +
                "&scope=${DRIVE_APPDATA_SCOPE.percentEncoded()}" +
                "&code_challenge=${codeChallenge(verifier)}" +
                "&code_challenge_method=S256"
            suspendCancellableCoroutine { cont ->
                val session = ASWebAuthenticationSession(
                    uRL = NSURL(string = authUrl),
                    callbackURLScheme = scheme,
                ) { callbackUrl, error ->
                    val code = callbackUrl?.let { url ->
                        NSURLComponents(uRL = url, resolvingAgainstBaseURL = false)
                            .queryItems
                            ?.filterIsInstance<NSURLQueryItem>()
                            ?.firstOrNull { it.name == "code" }
                            ?.value
                    }
                    if (error != null || code == null) {
                        cont.resume(Result.failure(BackupException(BackupError.AuthRequired)))
                    } else {
                        cont.resume(Result.success(code))
                    }
                }
                session.presentationContextProvider = AnchorProvider()
                session.prefersEphemeralWebBrowserSession = false
                cont.invokeOnCancellation { session.cancel() }
                session.start()
            }.getOrThrow()
        }

    private suspend fun exchangeCode(client: String, code: String, verifier: String): TokenDto =
        tokenRequest(
            "client_id=${client.percentEncoded()}" +
                "&code=${code.percentEncoded()}" +
                "&code_verifier=${verifier.percentEncoded()}" +
                "&redirect_uri=${"${reversedClientScheme(client)}:/oauth2redirect".percentEncoded()}" +
                "&grant_type=authorization_code",
        )

    private suspend fun refreshAccessToken(client: String, refreshToken: String): TokenDto =
        tokenRequest(
            "client_id=${client.percentEncoded()}" +
                "&refresh_token=${refreshToken.percentEncoded()}" +
                "&grant_type=refresh_token",
        )

    private suspend fun tokenRequest(form: String): TokenDto {
        val request = NSMutableURLRequest(uRL = NSURL(string = TOKEN_URL)).apply {
            setHTTPMethod("POST")
            setValue("application/x-www-form-urlencoded", forHTTPHeaderField = "Content-Type")
            setHTTPBody(form.encodeToByteArray().toNSData())
            setTimeoutInterval(30.0)
        }
        val body = suspendCancellableCoroutine { cont ->
            val task = NSURLSession.sharedSession.dataTaskWithRequest(
                request,
            ) { data: NSData?, response: NSURLResponse?, error: NSError? ->
                val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: -1
                if (error != null || status !in 200..299 || data == null) {
                    cont.resume(Result.failure(BackupException(BackupError.AuthRequired)))
                } else {
                    cont.resume(Result.success(data.toByteArray()))
                }
            }
            cont.invokeOnCancellation { task.cancel() }
            task.resume()
        }.getOrThrow()
        return json.decodeFromString(TokenDto.serializer(), body.decodeToString())
    }

    private fun reversedClientScheme(client: String): String {
        // "123-abc.apps.googleusercontent.com" → "com.googleusercontent.apps.123-abc"
        return client.split(".").reversed().joinToString(".")
    }

    private fun codeVerifier(): String = base64Url(secureRandom(64))

    private fun codeChallenge(verifier: String): String {
        val input = verifier.encodeToByteArray()
        val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
        input.usePinned { inPinned ->
            digest.usePinned { outPinned ->
                CC_SHA256(inPinned.addressOf(0), input.size.toUInt(), outPinned.addressOf(0).reinterpret())
            }
        }
        return base64Url(digest)
    }

    private fun base64Url(bytes: ByteArray): String {
        val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val sb = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else -1
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else -1
            sb.append(table[b0 shr 2])
            sb.append(table[((b0 and 0x3) shl 4) or (if (b1 >= 0) b1 shr 4 else 0)])
            if (b1 >= 0) sb.append(table[((b1 and 0xF) shl 2) or (if (b2 >= 0) b2 shr 6 else 0)])
            if (b2 >= 0) sb.append(table[b2 and 0x3F])
            i += 3
        }
        return sb.toString() // unpadded, per RFC 7636
    }

    private fun secureRandom(length: Int): ByteArray {
        val bytes = ByteArray(length)
        val status = bytes.usePinned {
            SecRandomCopyBytes(kSecRandomDefault, length.toULong(), it.addressOf(0))
        }
        check(status == errSecSuccess) { "SecRandomCopyBytes failed: $status" }
        return bytes
    }

    private fun String.percentEncoded(): String {
        @Suppress("CAST_NEVER_SUCCEEDS")
        return (this as NSString).stringByAddingPercentEncodingWithAllowedCharacters(
            NSCharacterSet.alphanumericCharacterSet,
        ) ?: this
    }

    private class AnchorProvider :
        DarwinNSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
        override fun presentationAnchorForWebAuthenticationSession(
            session: ASWebAuthenticationSession,
        ): ASPresentationAnchor = UIApplication.sharedApplication.keyWindow
            ?: (UIApplication.sharedApplication.windows.firstOrNull() as ASPresentationAnchor)
    }

    @Serializable
    private data class TokenDto(
        @kotlinx.serialization.SerialName("access_token") val accessToken: String,
        @kotlinx.serialization.SerialName("expires_in") val expiresIn: Long = 3600,
        @kotlinx.serialization.SerialName("refresh_token") val refreshToken: String? = null,
    )

    private companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        const val EXPIRY_MARGIN_MILLIS = 60_000L
    }
}
