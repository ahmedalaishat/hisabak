package com.hisabak.feature.sms.domain

/**
 * Specificity ranking: templates with more literal anchor text are tried first, so a generic
 * user template ("{amount} at {brand}") can never shadow a real bank format — it only catches
 * what nothing more specific wanted. User templates win ties over defaults (they were made
 * deliberately for this user's bank); beyond that the input order (creation order) is kept.
 */
fun rankTemplates(templates: List<SmsParserTemplate>): List<SmsParserTemplate> =
    templates.sortedWith(
        compareByDescending<SmsParserTemplate> { literalAnchorLength(it.pattern) }
            .thenBy { it.isDefault },
    )

/** Non-whitespace literal characters outside `{placeholder}` markers. */
fun literalAnchorLength(pattern: String): Int =
    pattern.replace(PLACEHOLDER, "").count { !it.isWhitespace() }

private val PLACEHOLDER = Regex("\\{[^}]*\\}")
