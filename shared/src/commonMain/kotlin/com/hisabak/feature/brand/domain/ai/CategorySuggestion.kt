package com.hisabak.feature.brand.domain.ai

import com.hisabak.feature.category.domain.Category
import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.feature.category.domain.CategoryColor
import com.hisabak.feature.category.domain.CategoryVocabulary
import com.hisabak.feature.category.domain.iconForCategoryName

/** A sanitized, ready-to-apply suggestion — always confirm-first, never auto-applied. */
sealed interface CategorySuggestion {
    data class Existing(val category: Category) : CategorySuggestion
    data class New(
        val name: String,
        val type: CategoryType,
        val color: String,
        val icon: String,
    ) : CategorySuggestion
}

/**
 * Deterministic backstop under the prompt-side rules (same philosophy as `canonicalizeBrand`):
 * a claimed existing name must snap to a real category (case-insensitive exact, then edit
 * distance ≤ 2 for typos); a proposed new category gets its type/color/icon validated against
 * the real vocabularies (fallbacks: expenses/gray/wallet), and a "new" name that actually
 * matches an existing category becomes an [CategorySuggestion.Existing] — the model shouldn't
 * duplicate what the user already has. Anything unusable is null, never an error.
 */
fun sanitizeCategorySuggestion(
    raw: AiRawCategorySuggestion,
    categories: List<Category>,
): CategorySuggestion? {
    raw.existingName?.trim()?.takeIf { it.isNotEmpty() }?.let { claimed ->
        matchCategory(claimed, categories)?.let { return CategorySuggestion.Existing(it) }
    }

    val name = raw.newName?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_NAME_LENGTH }
        ?: return null
    matchCategory(name, categories)?.let { return CategorySuggestion.Existing(it) }

    return CategorySuggestion.New(
        name = name,
        type = CategoryType.entries.firstOrNull { it.name.equals(raw.newType, ignoreCase = true) }
            ?: CategoryType.EXPENSES,
        // Distinctness is something only the app can know, so the model's colour is ignored
        // outright: a new category takes the hue furthest from the ones already in use, exactly
        // as a hand-made one does. Otherwise an accepted suggestion could land on a colour that
        // is already a slice of the same donut.
        color = CategoryColor.customKey(
            CategoryColor.mostDistinctHue(categories.mapNotNull { CategoryColor.hueFor(it.color) }),
        ),
        // The model only picks from the 12 icons its prompt lists, but the catalogue holds 144.
        // Matching the name it just produced against the catalogue's own keywords gets a far
        // better glyph for free — no prompt growth, no extra inference, deterministic. The
        // model's own choice is the fallback when the name matches nothing.
        icon = iconForCategoryName(name)
            ?: raw.newIcon?.lowercase()?.takeIf { it in CategoryVocabulary.icons }
            ?: Category.DEFAULT_ICON,
    )
}

private fun matchCategory(name: String, categories: List<Category>): Category? {
    categories.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return it }
    if (name.length < MIN_TYPO_LENGTH) return null
    return categories.firstOrNull {
        it.name.length >= MIN_TYPO_LENGTH &&
            levenshtein(name.lowercase(), it.name.lowercase()) <= MAX_TYPO_DISTANCE
    }
}

private fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    var previous = IntArray(b.length + 1) { it }
    val current = IntArray(b.length + 1)
    for (i in 1..a.length) {
        current[0] = i
        for (j in 1..b.length) {
            val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = minOf(previous[j] + 1, current[j - 1] + 1, substitution)
        }
        previous = current.copyInto(IntArray(b.length + 1))
    }
    return previous[b.length]
}

private const val MAX_NAME_LENGTH = 30
private const val MIN_TYPO_LENGTH = 4
private const val MAX_TYPO_DISTANCE = 2
