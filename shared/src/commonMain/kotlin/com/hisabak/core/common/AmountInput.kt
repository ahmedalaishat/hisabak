package com.hisabak.core.common

/**
 * Normalizes a free-typed amount into a parseable, `.`-decimal string for [Money.parseMajor].
 *
 * Keeps digits and treats the **last** `.` or `,` as the decimal point; any earlier separators are
 * grouping and dropped. This is locale-free yet handles both `1,234.56` and the comma-decimal
 * `1.234,56` / `12,50` that a non-en keyboard produces — so users in comma-decimal locales can
 * actually enter cents. A trailing separator is preserved ("12." stays "12.") so it doesn't vanish
 * mid-typing. The one ambiguous case — a lone `,` with no `.` (e.g. "1,234") — is read as a decimal.
 */
fun sanitizeAmountInput(raw: String): String {
    val chars = raw.filter { it.isDigit() || it == '.' || it == ',' }
    val lastSeparator = chars.indexOfLast { it == '.' || it == ',' }
    if (lastSeparator < 0) return chars
    val whole = chars.substring(0, lastSeparator).filter { it.isDigit() }
    val fraction = chars.substring(lastSeparator + 1).filter { it.isDigit() }
    return "$whole.$fraction"
}
