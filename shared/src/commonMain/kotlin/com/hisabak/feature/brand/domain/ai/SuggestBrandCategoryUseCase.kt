package com.hisabak.feature.brand.domain.ai

import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.feature.category.domain.CategoryRepository
import kotlinx.coroutines.flow.first

/**
 * Asks the on-device model for a category suggestion for [brandName] against the user's real
 * categories, then sanitizes the answer ([sanitizeCategorySuggestion]). Null means "no
 * suggestion" — unavailable model, junk output, or nothing sensible to say; the caller shows
 * nothing. Confirm-first: the result is only ever rendered as a tappable chip.
 */
class SuggestBrandCategoryUseCase(
    private val suggester: AiCategorySuggester,
    private val categoryRepository: CategoryRepository,
    private val analytics: Analytics,
) {
    suspend fun isAvailable(): Boolean = suggester.isReady()

    suspend operator fun invoke(brandName: String): CategorySuggestion? {
        if (!suggester.isReady()) {
            analytics.log(AnalyticsEvent.AiCategoryFailed("unavailable"))
            return null
        }
        val categories = categoryRepository.observeAll(type = null, search = null).first()
        val options = categories.map { AiCategoryOption(it.name, it.type.name.lowercase()) }
        val raw = suggester.suggest(brandName.trim(), options)
        val suggestion = raw?.let { sanitizeCategorySuggestion(it, categories) }
        if (suggestion == null) {
            analytics.log(AnalyticsEvent.AiCategoryFailed("model_empty"))
            return null
        }
        val kind = if (suggestion is CategorySuggestion.Existing) "existing" else "new"
        analytics.log(AnalyticsEvent.AiCategorySuggested(kind))
        return suggestion
    }
}
