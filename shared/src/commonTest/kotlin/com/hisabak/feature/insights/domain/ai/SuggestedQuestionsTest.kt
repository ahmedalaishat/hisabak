package com.hisabak.feature.insights.domain.ai

import com.hisabak.feature.insights.domain.deriveInsights
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SuggestedQuestionsTest {

    @Test
    fun `chips follow the findings with one per category and end with the general question`() {
        val insights = deriveInsights(summary())   // dining over limit + up; fuel; savings; uncategorized

        val chips = suggestedQuestions(insights)

        assertTrue(chips.size <= MAX_SUGGESTED_QUESTIONS)
        assertEquals(SuggestedQuestion.Kind.StayUnderLimit, chips.first().kind)
        assertEquals("dining", chips.first().category?.id?.value)
        assertEquals(1, chips.count { it.category?.id?.value == "dining" })
        assertEquals(SuggestedQuestion.Kind.FocusOn, chips.last().kind)
    }

    @Test
    fun `an empty review still offers the general question`() {
        assertEquals(listOf(SuggestedQuestion(SuggestedQuestion.Kind.FocusOn)), suggestedQuestions(emptyList()))
    }
}
