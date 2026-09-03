package com.hisabak.feature.insights.domain.ai

import com.hisabak.feature.insights.domain.Insight
import com.hisabak.feature.insights.domain.InsightType

/** At most this many chips: more is a menu, and the free-text field is there for the rest. */
const val MAX_SUGGESTED_QUESTIONS = 4

/**
 * Questions worth asking about *this* review, derived from its findings so the entry point is an
 * insight rather than a blank box. Ordered by the findings' own order (severity first); one per
 * category; the general "what should I focus on" is always last and always present.
 */
fun suggestedQuestions(insights: List<Insight>): List<SuggestedQuestion> {
    val out = mutableListOf<SuggestedQuestion>()
    val seen = mutableSetOf<String>()
    for (insight in insights) {
        if (out.size >= MAX_SUGGESTED_QUESTIONS - 1) break
        val category = insight.category
        if (category != null && !seen.add(category.id.value)) continue
        val kind = when (insight.type) {
            InsightType.OverLimit, InsightType.NearLimit -> SuggestedQuestion.Kind.StayUnderLimit
            InsightType.SpendUp, InsightType.NewSpend -> SuggestedQuestion.Kind.WhyUp
            InsightType.LargestCategory -> SuggestedQuestion.Kind.WhereToCut
            InsightType.SavingsRate -> SuggestedQuestion.Kind.ImproveSavings
            InsightType.Uncategorized -> SuggestedQuestion.Kind.Uncategorized
            InsightType.SpendDown -> continue
        }
        out += SuggestedQuestion(kind, category)
    }
    out += SuggestedQuestion(SuggestedQuestion.Kind.FocusOn)
    return out
}
