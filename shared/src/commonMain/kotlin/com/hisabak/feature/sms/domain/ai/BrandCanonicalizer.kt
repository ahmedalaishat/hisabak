package com.hisabak.feature.sms.domain.ai

/**
 * Deterministic backstop under the prompt-side brand hints: snap the model's merchant string
 * to an existing brand name so the pre-filled result shows exactly what will be linked. Match
 * order — case-insensitive exact, then substring either way (the same containment rule
 * `FindOrCreateBrandUseCase.findByNameLike` applies at link time), then a small edit
 * distance for typos. [knownBrands] is usage-ordered, so ties go to the most-used brand.
 */
fun canonicalizeBrand(raw: String, knownBrands: List<String>): String {
    knownBrands.firstOrNull { it.equals(raw, ignoreCase = true) }?.let { return it }
    knownBrands.firstOrNull {
        raw.contains(it, ignoreCase = true) || it.contains(raw, ignoreCase = true)
    }?.let { return it }
    if (raw.length >= MIN_TYPO_LENGTH) {
        knownBrands.firstOrNull {
            it.length >= MIN_TYPO_LENGTH &&
                levenshtein(raw.lowercase(), it.lowercase()) <= MAX_TYPO_DISTANCE
        }?.let { return it }
    }
    return raw
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

private const val MIN_TYPO_LENGTH = 4
private const val MAX_TYPO_DISTANCE = 2
