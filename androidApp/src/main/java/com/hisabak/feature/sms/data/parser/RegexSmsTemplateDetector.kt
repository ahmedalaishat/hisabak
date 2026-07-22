package com.hisabak.feature.sms.data.parser

import com.hisabak.feature.sms.domain.SmsTemplate
import com.hisabak.feature.sms.domain.SmsTemplateDetector

/**
 * Mirrors `App\BusinessLogic\SmsTemplateDetector`.
 * Picks the first configured template whose masked regex matches the SMS body,
 * then extracts placeholder values into a key→value map the parser can consume.
 *
 * The text around placeholders is matched literally (escaped), so template punctuation
 * such as a trailing `.` is a real dot — which also bounds the final placeholder.
 */
class RegexSmsTemplateDetector(
    patterns: List<String>,
) : SmsTemplateDetector {

    private data class CompiledTemplate(
        val source: String,
        val keys: List<String>,
        val regex: Regex,
    )

    private val compiled: List<CompiledTemplate> = patterns.map { pattern ->
        val keys = PLACEHOLDER.findAll(pattern).map { it.groupValues[1] }.toList()
        val masked = buildString {
            var last = 0
            for (match in PLACEHOLDER.findAll(pattern)) {
                append(Regex.escape(pattern.substring(last, match.range.first)))
                append("(.*?)")
                last = match.range.last + 1
            }
            append(Regex.escape(pattern.substring(last)))
        }
        CompiledTemplate(
            source = pattern,
            keys = keys,
            regex = Regex(masked, setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)),
        )
    }

    override fun detect(body: String): SmsTemplate? {
        for (tpl in compiled) {
            val match = tpl.regex.find(body) ?: continue
            val fields = buildMap {
                tpl.keys.forEachIndexed { index, key ->
                    val value = match.groupValues.getOrNull(index + 1)?.trim().orEmpty()
                    if (value.isNotEmpty() && key != "ignore") put(key, value)
                }
            }
            return SmsTemplate(pattern = tpl.source, fields = fields)
        }
        return null
    }

    companion object {
        private val PLACEHOLDER = Regex("\\{([^}]*)\\}")
    }
}
