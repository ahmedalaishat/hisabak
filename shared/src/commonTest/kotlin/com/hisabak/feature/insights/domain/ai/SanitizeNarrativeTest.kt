package com.hisabak.feature.insights.domain.ai

import com.hisabak.feature.category.domain.CategoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SanitizeNarrativeTest {

    private val s = summary()

    @Test
    fun `an unknown category drops the item rather than showing it period-wide`() {
        val out = sanitizeNarrative(listOf(raw(categoryId = "ghost"), raw(categoryId = null, headline = "You saved 34%")), s)

        assertEquals(listOf("You saved 34%"), out.map { it.headline })
        assertNull(out.single().category)
    }

    @Test
    fun `a known category is resolved whole so the card needs no lookup`() {
        val out = sanitizeNarrative(listOf(raw(categoryId = "dining")), s)

        val category = out.single().category!!
        assertEquals(CategoryId("dining"), category.id)
        assertEquals("Category dining", category.name)
        assertEquals("blue", category.color)
    }

    @Test
    fun `blank headlines drop and long text is cut to what the card shows`() {
        val out = sanitizeNarrative(
            listOf(
                raw(headline = "   "),
                raw(categoryId = "fuel", headline = "x".repeat(200), detail = "y".repeat(500)),
            ),
            s,
        )

        assertEquals(1, out.size)
        assertEquals(MAX_HEADLINE_LENGTH, out.single().headline.length)
        assertEquals(MAX_DETAIL_LENGTH, out.single().detail.length)
    }

    @Test
    fun `one item per category and at most five in total`() {
        val many = List(3) { raw(categoryId = null, headline = "general $it") } +
            raw(categoryId = "dining", headline = "first") +
            raw(categoryId = "dining", headline = "second") +
            raw(categoryId = "fuel", headline = "fuel") +
            raw(categoryId = null, headline = "general 3")

        val out = sanitizeNarrative(many, s)

        assertEquals(MAX_NARRATIVE_ITEMS, out.size)
        // Period-wide remarks are distinct (savings rate, uncategorized) and are not collapsed.
        assertEquals(3, out.count { it.category == null })
        assertTrue(out.none { it.headline == "second" })
        assertTrue(out.none { it.headline == "general 3" })
    }

    @Test
    fun `a suggestion must be plausible for its category`() {
        fun suggest(minor: Long?, id: String = "dining") =
            sanitizeNarrative(listOf(raw(categoryId = id, suggestedLimitMinor = minor)), s).single().suggestedLimitMinor

        assertEquals(1_600_00L, suggest(1_600_37))               // rounded to whole major units
        assertNull(suggest(1_500_00))                             // same as the current limit: no-op
        assertNull(suggest(0))
        assertNull(suggest(-500_00))
        val ceiling = 1_800_00L * MAX_SUGGESTION_MULTIPLE
        assertNull(suggest(ceiling + 100))                        // beyond anything the category did
        assertEquals(ceiling, suggest(ceiling))
        assertEquals(500_00L, suggest(500_00, id = "fuel"))       // a category with no limit may get one
    }

    @Test
    fun `a suggestion is a monthly cap so a yearly review drops it`() {
        val yearly = summary(period = com.hisabak.core.common.SummaryPeriod.CURRENT_YEAR)

        val out = sanitizeNarrative(listOf(raw(categoryId = "dining", suggestedLimitMinor = 1_600_00)), yearly)

        assertNull(out.single().suggestedLimitMinor)
    }

    @Test
    fun `a period-wide item never carries a suggestion`() {
        val out = sanitizeNarrative(listOf(raw(categoryId = null, suggestedLimitMinor = 1_000_00)), s)

        assertNull(out.single().suggestedLimitMinor)
    }
}
