package com.hisabak.feature.brand.domain

/**
 * Snaps a machine-extracted merchant string to an existing brand name. Match order —
 * case-insensitive exact, then substring either way, then a small edit distance for typos.
 * Returns [raw] unchanged when nothing matches.
 *
 * Used at both ends of the pipeline so they cannot disagree: at suggest time it makes the
 * pre-filled result show exactly what will be linked, and at link time
 * [com.hisabak.feature.brand.domain.usecase.ResolveBrandUseCase] applies the same ladder to
 * whatever a regex template captured. Those two used to differ — link time had containment only,
 * via an unordered `LIKE` — which is how one merchant ended up with two brands.
 *
 * [knownBrands] must be usage-ordered: containment and typo distance both admit several
 * candidates, and most-used-first is what makes the choice deterministic rather than
 * whatever row the database happened to return first.
 *
 * Deliberately not applied to user-typed names: at edit distance 2 "Noon" and "Moon" are the
 * same brand, which is fine for a bank's merchant string and wrong for something a user typed.
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
