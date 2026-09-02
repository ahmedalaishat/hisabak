package com.hisabak.feature.brand.platform

/**
 * Swift-side seam for on-device category suggestions (Apple Foundation Models is Swift-only —
 * see `FoundationModelsCategorySuggester.swift`). Same bridge rules as `AiSmsBridge`: no
 * exceptions cross the boundary — failure is a nil completion; primitives only. [categories]
 * arrives pre-formatted as "Name (type)" lines for the prompt.
 */
interface AiCategoryBridge {
    fun isAvailable(): Boolean
    fun suggest(
        brandName: String,
        categories: List<String>,
        completion: (AiCategoryBridgeResult?) -> Unit,
    )
}

class AiCategoryBridgeResult(
    val existingName: String?,
    val newName: String?,
    val newType: String?,
    val newIcon: String?,
)
