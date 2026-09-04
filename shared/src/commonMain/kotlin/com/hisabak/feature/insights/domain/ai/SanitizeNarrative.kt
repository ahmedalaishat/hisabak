package com.hisabak.feature.insights.domain.ai

import com.hisabak.feature.dashboard.domain.isSingleMonth
import com.hisabak.feature.insights.domain.InsightCategory
import com.hisabak.feature.insights.domain.InsightsSummary

/** The screen shows at most this many narrative cards; anything past it is padding. */
const val MAX_NARRATIVE_ITEMS = 5

const val MAX_HEADLINE_LENGTH = 80
const val MAX_DETAIL_LENGTH = 240

/**
 * A proposed cap further than this from anything the category has actually done is not a
 * suggestion, it is a number: the model may only propose within reach of the spend it saw.
 */
const val MAX_SUGGESTION_MULTIPLE = 3

/**
 * Acceptance rules for the model's narrative — the counterpart of the parser's `sanitize`.
 *
 * The model returns prose the app renders as-is, so the wrong-sentence risk is real but bounded:
 * with no tools and no writes, the worst a bad reply can do is be shown. What this step prevents is
 * a wrong sentence becoming a **wrong card** — one pointing at a category the user doesn't have,
 * or proposing a cap unrelated to the figures. Rules:
 *
 * - a `categoryId` must name a category in [summary]; an unknown one **drops the item** rather than
 *   demoting it to period-wide, because its text is about a category the user cannot see;
 * - blank headlines drop the item; text is trimmed and cut to the length the card can show;
 * - one item per category — the first wins, since the model ordered by importance; period-wide
 *   items (savings rate, uncategorized spend) are distinct remarks and are not collapsed;
 * - a suggestion needs a category, must be positive, must differ from the current limit, and must
 *   sit within [MAX_SUGGESTION_MULTIPLE] of the largest figure the category has (spent, prior,
 *   limit); it is rounded to whole major units, since a cap of 1,600.37 reads as a mistake. A
 *   suggestion is a **monthly** cap and the editor sets a monthly limit, so it is only kept for a
 *   single-month period — against a year's figures the model would propose a year's number;
 * - at most [MAX_NARRATIVE_ITEMS] items.
 */
fun sanitizeNarrative(items: List<RawNarrativeInsight>, summary: InsightsSummary): List<NarrativeInsight> {
    val byId = summary.categories.associateBy { it.id.value }
    val seen = mutableSetOf<String>()
    val out = mutableListOf<NarrativeInsight>()
    for (raw in items) {
        if (out.size >= MAX_NARRATIVE_ITEMS) break
        val headline = raw.headline.trim().take(MAX_HEADLINE_LENGTH)
        if (headline.isEmpty()) continue
        val spend = raw.categoryId?.let { id -> byId[id] ?: continue }
        if (spend != null && !seen.add(spend.id.value)) continue
        val suggestion = raw.suggestedLimitMinor?.takeIf { spend != null && summary.period.isSingleMonth }?.let { proposed ->
            val ceiling = MAX_SUGGESTION_MULTIPLE * maxOf(spend!!.spentMinor, spend.priorMinor ?: 0L, spend.limitMinor ?: 0L)
            val rounded = proposed / 100 * 100
            rounded.takeIf { it > 0 && it <= ceiling && it != spend.limitMinor }
        }
        out += NarrativeInsight(
            category = spend?.let { InsightCategory(it.id, it.name, it.color, it.icon) },
            headline = headline,
            detail = raw.detail.trim().take(MAX_DETAIL_LENGTH),
            suggestedLimitMinor = suggestion,
        )
    }
    return out
}
