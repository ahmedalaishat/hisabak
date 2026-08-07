package com.hisabak.testutil

import com.hisabak.feature.brand.domain.ai.AiCategoryOption
import com.hisabak.feature.brand.domain.ai.AiCategorySuggester
import com.hisabak.feature.brand.domain.ai.AiRawCategorySuggestion

class FakeAiCategorySuggester : AiCategorySuggester {
    var ready: Boolean = true
    var result: AiRawCategorySuggestion? = null
    val suggestedBrands = mutableListOf<String>()
    var lastCategories: List<AiCategoryOption> = emptyList()
        private set

    override suspend fun isReady(): Boolean = ready

    override suspend fun suggest(
        brandName: String,
        categories: List<AiCategoryOption>,
    ): AiRawCategorySuggestion? {
        suggestedBrands += brandName
        lastCategories = categories
        return result
    }
}
