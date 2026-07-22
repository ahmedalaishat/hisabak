package com.hisabak.core.common

/** Build-time flags the shared layer needs; androidApp builds it from `BuildConfig`. */
data class AppConfig(
    val seedData: Boolean,
    val smsAutoCapture: Boolean,
    val isDebug: Boolean,
    val versionCode: Int,
)
