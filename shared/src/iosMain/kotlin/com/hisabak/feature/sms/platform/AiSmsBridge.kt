package com.hisabak.feature.sms.platform

/**
 * Swift-side seam for on-device AI SMS parsing (Apple Foundation Models is Swift-only, like
 * CryptoKit — see `FoundationModelsSmsParser.swift`). Same bridge rules as `GcmCipher`: no
 * exceptions cross the boundary — failure is a nil completion; primitives only, with has-flags
 * instead of boxed optionals for the numeric field.
 */
interface AiSmsBridge {
    fun isAvailable(): Boolean
    fun parse(body: String, knownBrands: List<String>, completion: (AiSmsBridgeResult?) -> Unit)
    fun parseFreeText(
        text: String,
        knownBrands: List<String>,
        todayIso: String,
        completion: (AiSmsBridgeResult?) -> Unit,
    )
}

class AiSmsBridgeResult(
    val brandName: String?,
    val amountMinor: Long,
    val hasAmount: Boolean,
    val currencyCode: String?,
    val dateIso: String?,
)
