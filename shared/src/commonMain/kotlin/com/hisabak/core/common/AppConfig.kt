package com.hisabak.core.common

/** Build-time flags the shared layer needs; androidApp builds it from `BuildConfig`. */
data class AppConfig(
    val seedData: Boolean,
    val smsAutoCapture: Boolean,
    val isDebug: Boolean,
    val versionCode: Int,
    /** Product flavor ("prod" | "staging") — scopes the Drive backup file per flavor. */
    val flavor: String,
    /** Parse-service base URL; blank disables the remote parser. */
    val parseServiceUrl: String = "",
    /** Shared secret for the parse service; blank disables the remote parser. */
    val parseServiceToken: String = "",
)
