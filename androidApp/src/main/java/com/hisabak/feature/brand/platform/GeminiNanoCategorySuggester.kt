package com.hisabak.feature.brand.platform

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.hisabak.feature.brand.domain.ai.AiCategoryOption
import com.hisabak.feature.brand.domain.ai.AiCategorySuggester
import com.hisabak.feature.brand.domain.ai.AiRawCategorySuggestion
import com.hisabak.feature.category.domain.CategoryVocabulary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * [AiCategorySuggester] over Gemini Nano via the ML Kit GenAI Prompt API — same OS-managed
 * model and availability semantics as `GeminiNanoSmsParser` (flagship devices only; a
 * DOWNLOADABLE model starts a background download and stays unavailable this session).
 */
class GeminiNanoCategorySuggester(private val appScope: CoroutineScope) : AiCategorySuggester {

    private val model by lazy { Generation.getClient() }
    private var ready = false

    override suspend fun isReady(): Boolean {
        if (ready) return true
        val status = runCatching { model.checkStatus() }.getOrDefault(FeatureStatus.UNAVAILABLE)
        return when (status) {
            FeatureStatus.AVAILABLE -> {
                ready = true
                true
            }
            FeatureStatus.DOWNLOADABLE -> {
                appScope.launch { runCatching { model.download().collect {} } }
                false
            }
            else -> false
        }
    }

    override suspend fun suggest(
        brandName: String,
        categories: List<AiCategoryOption>,
    ): AiRawCategorySuggestion? = runCatching {
        val response = model.generateContent(promptFor(brandName, categories))
        response.candidates.firstOrNull()?.text?.let(::decode)
    }.getOrNull()

    private fun promptFor(brandName: String, categories: List<AiCategoryOption>) = buildString {
        appendLine(
            """
            You pick a budget category for a merchant or brand name. The name may be in English or Arabic.
            Reply with ONLY a JSON object, exactly this shape:
            {"existing": string or null, "name": string or null, "type": string or null, "color": string or null, "icon": string or null}
            Rules:
            - If one of the user's categories fits the brand, set "existing" to that category's name exactly as listed and every other field to null.
            - Otherwise propose a new category: "name" is a short category name (1-2 words, same language as the brand), with the best fitting "type", "color", and "icon" from the lists below.
            - type: one of income, expenses, savings, investment.
            - color: one of ${CategoryVocabulary.colors.joinToString(", ")}.
            - icon: one of ${CategoryVocabulary.icons.joinToString(", ")}.
            - If the brand name is meaningless or you cannot tell what it sells, use null for every field.
            """.trimIndent(),
        )
        if (categories.isNotEmpty()) {
            appendLine("The user's categories:")
            categories.forEach { appendLine("- ${it.name} (${it.type})") }
        }
        appendLine()
        appendLine("Brand:")
        append(brandName)
    }

    /** Defensive decode: models wrap JSON in prose/fences at will; null on anything unusable. */
    private fun decode(raw: String): AiRawCategorySuggestion? {
        val start = raw.indexOf('{').takeIf { it >= 0 } ?: return null
        val end = raw.lastIndexOf('}').takeIf { it > start } ?: return null
        val json = runCatching { Json.parseToJsonElement(raw.substring(start, end + 1)).jsonObject }
            .getOrNull() ?: return null

        fun string(name: String) = (json[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

        return AiRawCategorySuggestion(
            existingName = string("existing"),
            newName = string("name"),
            newType = string("type"),
            newColor = string("color"),
            newIcon = string("icon"),
        )
    }
}
