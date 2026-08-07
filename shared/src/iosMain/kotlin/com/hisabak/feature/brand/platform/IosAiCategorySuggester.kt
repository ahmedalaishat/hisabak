package com.hisabak.feature.brand.platform

import com.hisabak.feature.brand.domain.ai.AiCategoryOption
import com.hisabak.feature.brand.domain.ai.AiCategorySuggester
import com.hisabak.feature.brand.domain.ai.AiRawCategorySuggestion
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** [AiCategorySuggester] over the Swift Foundation Models bridge injected via `startIosApp`. */
class IosAiCategorySuggester(private val bridge: AiCategoryBridge) : AiCategorySuggester {

    override suspend fun isReady(): Boolean = bridge.isAvailable()

    override suspend fun suggest(
        brandName: String,
        categories: List<AiCategoryOption>,
    ): AiRawCategorySuggestion? = suspendCancellableCoroutine { continuation ->
        bridge.suggest(brandName, categories.map { "${it.name} (${it.type})" }) { result ->
            continuation.resume(
                result?.let {
                    AiRawCategorySuggestion(
                        existingName = it.existingName,
                        newName = it.newName,
                        newType = it.newType,
                        newColor = it.newColor,
                        newIcon = it.newIcon,
                    )
                },
            )
        }
    }
}
