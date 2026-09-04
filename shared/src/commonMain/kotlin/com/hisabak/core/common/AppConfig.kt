package com.hisabak.core.common

/** Build-time flags the shared layer needs; androidApp builds it from `BuildConfig`. */
data class AppConfig(
    val seedData: Boolean,
    val smsAutoCapture: Boolean,
    val isDebug: Boolean,
    val versionCode: Int,
    /** Product flavor ("prod" | "staging") — scopes the Drive backup file per flavor. */
    val flavor: String,
    /** Service base URL; blank disables every remote AI feature (parsing, insights). */
    val parseServiceUrl: String = "",
    /** Shared secret for the service; blank disables every remote AI feature. */
    val parseServiceToken: String = "",
) {
    /** Whether this build can reach the service at all — the gate for showing any opt-in for it. */
    val hasParseService: Boolean get() = parseServiceUrl.isNotBlank() && parseServiceToken.isNotBlank()
}
