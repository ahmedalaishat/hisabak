package com.hisabak.feature.brand.domain.ai

/**
 * On-device generative suggester for a brand's category. Platform implementations (Gemini Nano
 * on Android, Apple Foundation Models on iOS) own their prompt/output idioms; this contract
 * fixes the semantics: the model either names one of the user's [AiCategoryOption]s
 * ([AiRawCategorySuggestion.existingName]) or drafts a new category (name/type/color/icon).
 * Implementations never throw — model failure is null. Inference runs fully on device; on
 * unsupported devices [isReady] is false and every suggestion affordance stays hidden.
 */
interface AiCategorySuggester {
    suspend fun isReady(): Boolean

    suspend fun suggest(brandName: String, categories: List<AiCategoryOption>): AiRawCategorySuggestion?
}

data class AiCategoryOption(val name: String, val type: String)

/** Raw model output, primitive-typed so it crosses the Swift bridge cleanly; sanitized in common code. */
data class AiRawCategorySuggestion(
    val existingName: String?,
    val newName: String?,
    val newType: String?,
    val newColor: String?,
    val newIcon: String?,
)
