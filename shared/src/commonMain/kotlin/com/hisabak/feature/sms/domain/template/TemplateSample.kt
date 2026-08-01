package com.hisabak.feature.sms.domain.template

import com.hisabak.feature.sms.domain.SmsTemplate

/**
 * Define-by-sample: the user tags character spans of a real message with roles; the pattern is
 * derived by replacing each tagged span with its placeholder and keeping everything else as
 * literal anchor text. Pure functions — the editor UI and use cases share them.
 */
enum class TagRole { AMOUNT, BRAND, DATE, TIME, SKIP }

/** Half-open character range [start, end) into the sample string. */
data class TagSpan(val role: TagRole, val start: Int, val end: Int)

private val TRIMMABLE = setOf(' ', '\n', '\t', '.', ',', ';', ':', '،', '؛')

private fun TagRole.placeholder(): String = when (this) {
    TagRole.AMOUNT -> "{amount}"
    TagRole.BRAND -> "{brand}"
    TagRole.DATE -> "{date}"
    TagRole.TIME -> "{time}"
    TagRole.SKIP -> "{ignore}"
}

private fun roleForKey(key: String): TagRole = when (key) {
    "amount" -> TagRole.AMOUNT
    "brand" -> TagRole.BRAND
    "date", "datetime" -> TagRole.DATE
    "time" -> TagRole.TIME
    else -> TagRole.SKIP // ignore, card, account, … — captured-and-dropped either way
}

/**
 * Derives the `{placeholder}` pattern from a tagged sample. Spans are trimmed of surrounding
 * whitespace/punctuation (tagging "MALL." means the brand is "MALL"), and same-role spans
 * separated only by whitespace merge into one placeholder ("HARDEES-WTC" + "MALL" → one
 * `{brand}`). Overlapping spans keep the earlier one.
 */
fun deriveTemplatePattern(sample: String, spans: List<TagSpan>): String {
    val cleaned = spans
        .mapNotNull { it.trimmedIn(sample) }
        .sortedBy { it.start }
        .dropOverlapping()
        .mergeAdjacentSameRole(sample)
    if (cleaned.isEmpty()) return sample
    return buildString {
        var cursor = 0
        for (span in cleaned) {
            append(sample.substring(cursor, span.start))
            append(span.role.placeholder())
            cursor = span.end
        }
        append(sample.substring(cursor))
    }
}

/**
 * Rebuilds the tagged spans of a stored user template by matching its pattern against the
 * sample it was defined from — the storage format stays just (pattern, sample), no span
 * serialization. Null when the pattern no longer matches the sample.
 */
fun reconstructSpans(pattern: String, sample: String): List<TagSpan>? {
    val (keys, regex) = compileMasked(pattern)
    // Entire-match, not find(): derivation keeps every character of the sample, so the pattern
    // must span it fully — which also forces lazy groups to backtrack past ambiguous literals
    // (a trailing "{ignore}." would otherwise stop at the decimal point of "8,342.27").
    val match = regex.matchEntire(sample) ?: return null
    return keys.mapIndexedNotNull { index, key ->
        val group = match.groups[index + 1] ?: return@mapIndexedNotNull null
        val range = group.range
        if (range.isEmpty()) null else TagSpan(roleForKey(key), range.first, range.last + 1)
    }
}

private fun compileMasked(pattern: String): Pair<List<String>, Regex> {
    val keys = PLACEHOLDER.findAll(pattern).map { it.groupValues[1] }.toList()
    val masked = buildString {
        var last = 0
        for (match in PLACEHOLDER.findAll(pattern)) {
            append(Regex.escape(pattern.substring(last, match.range.first)))
            // Trailing placeholder → greedy, mirroring RegexSmsTemplateDetector, so the editor
            // preview and the runtime detector always extract the same fields.
            append(if (match.range.last + 1 == pattern.length) "(.+)" else "(.*?)")
            last = match.range.last + 1
        }
        append(Regex.escape(pattern.substring(last)))
    }
    return keys to Regex(masked, setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
}

/**
 * Heuristic pre-tagging for a pasted sample: times, dates, a best-guess amount (a decimal near
 * a currency word wins, else the first decimal, else the first bare integer), remaining
 * number-ish tokens as Skip (balances, reference codes — leaving them literal would break
 * future matches), and the words after " at " as the brand. The user adjusts from here.
 */
fun suggestSpans(sample: String): List<TagSpan> {
    val taken = mutableListOf<TagSpan>()
    fun claim(role: TagRole, range: IntRange): Boolean {
        val span = TagSpan(role, range.first, range.last + 1)
        if (taken.any { it.overlaps(span) }) return false
        taken += span
        return true
    }

    TIME_TOKEN.findAll(sample).forEach { claim(TagRole.TIME, it.range) }
    DATE_TOKEN.findAll(sample).forEach { claim(TagRole.DATE, it.range) }

    val decimals = DECIMAL_TOKEN.findAll(sample).toList()
    val amount = decimals.firstOrNull { sample.hasCurrencyBefore(it.range.first) }
        ?: decimals.firstOrNull()
    if (amount != null) {
        claim(TagRole.AMOUNT, amount.range)
        decimals.filter { it !== amount }.forEach { claim(TagRole.SKIP, it.range) }
    }

    INTEGER_TOKEN.findAll(sample).forEach { match ->
        val role = if (amount == null && taken.none { it.role == TagRole.AMOUNT }) TagRole.AMOUNT else TagRole.SKIP
        claim(role, match.range)
    }

    BRAND_AFTER_AT.find(sample)?.groups?.get(1)?.let { group ->
        if (group.value.isNotBlank()) claim(TagRole.BRAND, group.range)
    }

    return taken.sortedBy { it.start }
}

/** Matches a single pattern against any message body, extracting its fields — the same masked
 *  compile the detector uses, kept pure so domain code can preview without the data layer. */
fun matchTemplate(pattern: String, body: String): SmsTemplate? {
    val (keys, regex) = compileMasked(pattern)
    val match = regex.find(body) ?: return null
    val fields = buildMap {
        keys.forEachIndexed { index, key ->
            val value = match.groupValues.getOrNull(index + 1)?.trim().orEmpty()
            if (value.isNotEmpty() && key != "ignore") put(key, value)
        }
    }
    return SmsTemplate(pattern = pattern, fields = fields)
}

/** The extraction a pattern yields on a sample, for the editor's live preview. */
fun previewFields(pattern: String, sample: String): SmsTemplate? {
    val spans = reconstructSpans(pattern, sample) ?: return null
    val fields = buildMap {
        for (span in spans) {
            val key = when (span.role) {
                TagRole.AMOUNT -> "amount"
                TagRole.BRAND -> "brand"
                TagRole.DATE -> "date"
                TagRole.TIME -> "time"
                TagRole.SKIP -> continue
            }
            put(key, sample.substring(span.start, span.end).trim())
        }
    }
    return SmsTemplate(pattern = pattern, fields = fields)
}

private fun TagSpan.trimmedIn(sample: String): TagSpan? {
    var s = start.coerceIn(0, sample.length)
    var e = end.coerceIn(0, sample.length)
    while (s < e && sample[s] in TRIMMABLE) s++
    while (e > s && sample[e - 1] in TRIMMABLE) e--
    return if (s < e) TagSpan(role, s, e) else null
}

private fun TagSpan.overlaps(other: TagSpan): Boolean = start < other.end && other.start < end

private fun List<TagSpan>.dropOverlapping(): List<TagSpan> {
    val result = mutableListOf<TagSpan>()
    for (span in this) {
        if (result.none { it.overlaps(span) }) result += span
    }
    return result
}

private fun List<TagSpan>.mergeAdjacentSameRole(sample: String): List<TagSpan> {
    val result = mutableListOf<TagSpan>()
    for (span in this) {
        val last = result.lastOrNull()
        if (last != null && last.role == span.role &&
            sample.substring(last.end, span.start).all { it.isWhitespace() }
        ) {
            result[result.lastIndex] = last.copy(end = span.end)
        } else {
            result += span
        }
    }
    return result
}

private fun String.hasCurrencyBefore(index: Int): Boolean {
    val windowStart = (index - 8).coerceAtLeast(0)
    val window = substring(windowStart, index).uppercase()
    return CURRENCY_MARKERS.any { it in window }
}

private val PLACEHOLDER = Regex("\\{([^}]*)\\}")
private val TIME_TOKEN = Regex("\\b\\d{1,2}:\\d{2}(:\\d{2})?\\b")
private val DATE_TOKEN = Regex("\\b\\d{1,4}[/-]\\d{1,2}[/-]\\d{1,4}\\b")
private val DECIMAL_TOKEN = Regex("\\d{1,3}(?:,\\d{3})+\\.\\d{1,2}|\\d+\\.\\d{1,2}")
private val INTEGER_TOKEN = Regex("(?<![\\d.,:/-])\\d{2,}(?![\\d.,:/-])")
private val BRAND_AFTER_AT = Regex("\\bat\\s+([^.,\\n]+)", RegexOption.IGNORE_CASE)
private val CURRENCY_MARKERS = listOf("AED", "USD", "EUR", "SAR", "GBP", "DHS", "$", "€", "£")
