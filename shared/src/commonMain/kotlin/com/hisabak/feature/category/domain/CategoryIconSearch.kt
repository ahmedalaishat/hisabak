package com.hisabak.feature.category.domain

/**
 * Search over [CategoryIconCatalog]. Matches English and Arabic keywords at once, so the language
 * the user types in doesn't have to match the app's.
 *
 * Arabic is normalized before comparing — hamza forms collapse to bare alef, taa marbuta to haa,
 * alef maqsura to yaa, and diacritics/tatweel are dropped — because people type "مطعم" or "اكل"
 * without the marks they'd need for an exact match.
 */
fun searchCategoryIcons(query: String): List<CategoryIconEntry> {
    val q = normalizeIconQuery(query)
    if (q.isEmpty()) return CategoryIconCatalog.entries

    val prefix = mutableListOf<CategoryIconEntry>()
    val contains = mutableListOf<CategoryIconEntry>()
    for ((entry, terms) in normalizedIndex) {
        when {
            terms.any { it.startsWith(q) } -> prefix += entry
            terms.any { it.contains(q) } -> contains += entry
        }
    }
    return prefix + contains
}

/** Lowercased, trimmed, and Arabic-normalized — the form both sides of a match are compared in. */
fun normalizeIconQuery(raw: String): String {
    val out = StringBuilder(raw.length)
    for (ch in raw.trim().lowercase()) {
        when (ch) {
            'أ', 'إ', 'آ', 'ٱ' -> out.append('ا')
            'ة' -> out.append('ه')
            'ى' -> out.append('ي')
            'ؤ' -> out.append('و')
            'ئ' -> out.append('ي')
            'ـ' -> Unit // tatweel is decoration
            in 'ً'..'ْ' -> Unit // harakat
            else -> out.append(ch)
        }
    }
    return out.toString()
}

private val normalizedIndex: List<Pair<CategoryIconEntry, List<String>>> by lazy {
    CategoryIconCatalog.entries.map { entry ->
        entry to (listOf(entry.key) + entry.keywords).map(::normalizeIconQuery)
    }
}

/**
 * The query to open the icon picker on for a category called [name], or "" when nothing matches.
 *
 * Whole-name first, then the most specific word in it: [searchCategoryIcons] compares the query
 * against each keyword as a unit, so "Coffee shops" matches nothing on its own even though
 * "coffee" matches perfectly. Longest word first, because it carries the meaning — "Family
 * support" should land on the family glyph, not on whatever "and" happens to hit.
 */
fun iconSearchSeed(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return ""
    if (searchCategoryIcons(trimmed).isNotEmpty()) return trimmed
    return normalizeIconQuery(trimmed)
        .split(' ', '&', '/', '-', ',')
        .filter { it.length >= MIN_WORD_LENGTH }
        .sortedByDescending { it.length }
        .firstOrNull { searchCategoryIcons(it).isNotEmpty() }
        .orEmpty()
}

/**
 * The best icon for a category called [name], or null when nothing matches. Used by the AI
 * suggestion path, which only gets to choose from the handful of icons its prompt can afford
 * to list.
 */
fun iconForCategoryName(name: String): String? =
    iconSearchSeed(name).takeIf { it.isNotEmpty() }?.let { searchCategoryIcons(it).firstOrNull()?.key }

/** Below this, a word is a preposition or an abbreviation and matches too loosely to trust. */
private const val MIN_WORD_LENGTH = 3
