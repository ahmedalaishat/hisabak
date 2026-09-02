package com.hisabak.feature.sms.domain.template

/**
 * Turns a successful AI parse into a reusable template: the spans the model implied are located
 * back in the message body, so the same bank format parses offline from then on.
 *
 * No second model call — the structural work (dates, times, and the number tokens that must
 * become `{ignore}` rather than literals) is [suggestSpans]' heuristic, and only the amount and
 * brand spans are overridden with what the model actually extracted.
 */
fun deriveAiSpans(body: String, rawBrand: String, amountMinor: Long): List<TagSpan>? {
    val amount = locateAmount(body, amountMinor) ?: return null
    val brand = locateBrand(body, rawBrand) ?: return null
    if (amount.overlaps(brand)) return null

    // The heuristic pass owns everything the model didn't tell us about. Its own amount/brand
    // guesses are demoted to Skip rather than dropped: it only ever tags variable text, so
    // leaving a rejected guess untagged would bake that message's balance into the pattern.
    val structural = suggestSpans(body)
        .filterNot { it.overlaps(amount) || it.overlaps(brand) }
        .map { if (it.role == TagRole.AMOUNT || it.role == TagRole.BRAND) it.copy(role = TagRole.SKIP) else it }

    val tagged = structural + amount + brand
    return (tagged + remainingNumbers(body, tagged)).sortedBy { it.start }
}

/**
 * Any digit run the passes above left untagged, as `{ignore}`.
 *
 * [suggestSpans]' integer token refuses a number touching punctuation, so a card number written
 * "card 1234." survives as literal text — and a rule carrying a literal card number matches
 * exactly one message, which is the one failure that makes this whole feature pointless. Numbers
 * in a bank alert are variable by nature, so sweeping the leftovers is safe; the anchor-length
 * gate still rejects a pattern that ends up mostly placeholders.
 */
private fun remainingNumbers(body: String, tagged: List<TagSpan>): List<TagSpan> =
    DIGIT_RUN.findAll(body)
        .map { TagSpan(TagRole.SKIP, it.range.first, it.range.last + 1) }
        .filterNot { candidate -> tagged.any { it.overlaps(candidate) } }
        .toList()

/**
 * The token whose value is exactly the parsed amount. Ties go to the one preceded by a currency
 * marker — "AED 50.00 ... balance 50.00" would otherwise pin the template to the balance.
 */
private fun locateAmount(body: String, amountMinor: Long): TagSpan? {
    val candidates = (AMOUNT_DECIMAL.findAll(body) + AMOUNT_INTEGER.findAll(body))
        .filter { it.value.toMinorOrNull() == amountMinor }
        .map { TagSpan(TagRole.AMOUNT, it.range.first, it.range.last + 1) }
        .toList()
    return candidates.firstOrNull { body.hasCurrencyMarkerBefore(it.start) } ?: candidates.firstOrNull()
}

/**
 * Where the model's merchant string sits in the body. Must be the *raw* model output, not the
 * canonicalized brand — canonicalization snaps to an existing brand name that often isn't the
 * text the message actually contains.
 */
private fun locateBrand(body: String, rawBrand: String): TagSpan? {
    val needle = rawBrand.trim()
    if (needle.isEmpty()) return null
    val start = body.indexOf(needle, ignoreCase = true)
    if (start < 0) return null
    return TagSpan(TagRole.BRAND, start, start + needle.length)
}

/** Minor units, via string math — `(0.29 * 100).toLong()` truncates to 28. */
private fun String.toMinorOrNull(): Long? {
    val cleaned = replace(",", "")
    val dot = cleaned.indexOf('.')
    val major = (if (dot < 0) cleaned else cleaned.substring(0, dot)).toLongOrNull() ?: return null
    if (dot < 0) return major * 100
    val fraction = cleaned.substring(dot + 1)
    if (fraction.isEmpty() || fraction.length > 2 || !fraction.all { it.isDigit() }) return null
    val cents = fraction.padEnd(2, '0').toLongOrNull() ?: return null
    return major * 100 + cents
}

private fun TagSpan.overlaps(other: TagSpan): Boolean = start < other.end && other.start < end

private fun String.hasCurrencyMarkerBefore(index: Int): Boolean {
    val window = substring((index - CURRENCY_LOOKBACK).coerceAtLeast(0), index).uppercase()
    return CURRENCY_MARKERS.any { it in window }
}

/** Mirrors TemplateSample's own token shapes so the two passes agree on what a number is. */
private val AMOUNT_DECIMAL = Regex("\\d{1,3}(?:,\\d{3})+\\.\\d{1,2}|\\d+\\.\\d{1,2}")
private val AMOUNT_INTEGER = Regex("(?<![\\d.,:/-])\\d+(?![\\d.,:/-])")
private val DIGIT_RUN = Regex("\\d{2,}")
private const val CURRENCY_LOOKBACK = 8
private val CURRENCY_MARKERS = listOf("AED", "USD", "EUR", "SAR", "GBP", "DHS", "$", "€", "£")
