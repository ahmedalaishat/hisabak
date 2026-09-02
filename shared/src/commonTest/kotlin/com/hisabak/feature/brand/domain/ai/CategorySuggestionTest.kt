package com.hisabak.feature.brand.domain.ai

import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.testutil.category
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import com.hisabak.feature.category.domain.Category
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import com.hisabak.feature.category.domain.CategoryColor

class CategorySuggestionTest {

    private val categories = listOf(
        category(id = "c1", name = "Groceries"),
        category(id = "c2", name = "Dining"),
    )

    private fun raw(
        existing: String? = null,
        name: String? = null,
        type: String? = null,
        icon: String? = null,
    ) = AiRawCategorySuggestion(existing, name, type, icon)

    @Test
    fun `claimed existing name snaps case-insensitively`() {
        val result = sanitizeCategorySuggestion(raw(existing = "groceries"), categories)
        assertEquals("c1", (result as CategorySuggestion.Existing).category.id.value)
    }

    @Test
    fun `claimed existing name snaps small typos`() {
        val result = sanitizeCategorySuggestion(raw(existing = "Grocries"), categories)
        assertEquals("c1", (result as CategorySuggestion.Existing).category.id.value)
    }

    @Test
    fun `unmatched existing claim falls through to the new proposal`() {
        val result = sanitizeCategorySuggestion(
            raw(existing = "Utilities", name = "Utilities", type = "expenses", icon = "home"),
            categories,
        )
        val new = result as CategorySuggestion.New
        assertEquals("Utilities", new.name)
        assertEquals(CategoryType.EXPENSES, new.type)
        // The name beats the model's pick: "Utilities" is a keyword of the lightbulb glyph, and
        // the model could only choose from the dozen icons its prompt lists.
        assertEquals("lightbulb", new.icon)
    }

    @Test
    fun `the model's icon is kept when the name matches nothing in the catalogue`() {
        val result = sanitizeCategorySuggestion(
            raw(existing = null, name = "Zzzq", type = "expenses", icon = "home"),
            categories,
        )
        assertEquals("home", (result as CategorySuggestion.New).icon)
    }

    @Test
    fun `an unusable model icon falls back to the default`() {
        val result = sanitizeCategorySuggestion(
            raw(existing = null, name = "Zzzq", type = "expenses", icon = "not-an-icon"),
            categories,
        )
        assertEquals(Category.DEFAULT_ICON, (result as CategorySuggestion.New).icon)
    }

    @Test
    fun `a new name matching an existing category becomes Existing`() {
        val result = sanitizeCategorySuggestion(
            raw(name = "DINING", type = "expenses", icon = "utensils"),
            categories,
        )
        assertEquals("c2", (result as CategorySuggestion.Existing).category.id.value)
    }

    @Test
    fun `invalid type and icon fall back to defaults`() {
        val result = sanitizeCategorySuggestion(
            raw(name = "Pets", type = "fun", icon = "dog"),
            categories,
        )
        val new = result as CategorySuggestion.New
        assertEquals(CategoryType.EXPENSES, new.type)
        assertEquals("wallet", new.icon)
    }

    @Test
    fun `a suggested category gets a hue of its own rather than a named colour`() {
        val used = listOf(
            category(id = "c1", name = "Groceries", color = "green"),
            category(id = "c2", name = "Dining", color = "red"),
        )
        val new = sanitizeCategorySuggestion(raw(name = "Pets"), used) as CategorySuggestion.New

        val hue = CategoryColor.hueOf(new.color)
        assertNotNull(hue, "expected a derived hue, got \"${'$'}{new.color}\"")
        used.forEach { existing ->
            val other = CategoryColor.hueFor(existing.color)
            if (other != null) {
                assertFalse(
                    CategoryColor.collides(hue, other),
                    "suggested hue ${'$'}hue collides with ${'$'}{existing.name}",
                )
            }
        }
    }

    @Test
    fun `the model no longer influences the colour`() {
        // Distinctness is the app's call — two accepted suggestions must not share a donut slice.
        val a = sanitizeCategorySuggestion(raw(name = "Pets"), categories) as CategorySuggestion.New
        val b = sanitizeCategorySuggestion(raw(name = "Pets"), categories) as CategorySuggestion.New
        assertEquals(a.color, b.color, "the same inputs must give the same colour")
    }

    @Test
    fun `type matches case-insensitively`() {
        val result = sanitizeCategorySuggestion(raw(name = "Deposits", type = "SAVINGS"), categories)
        assertEquals(CategoryType.SAVINGS, (result as CategorySuggestion.New).type)
    }

    @Test
    fun `nothing usable is null`() {
        assertNull(sanitizeCategorySuggestion(raw(), categories))
        assertNull(sanitizeCategorySuggestion(raw(existing = "  "), categories))
        assertNull(sanitizeCategorySuggestion(raw(name = "x".repeat(31)), categories))
    }

    @Test
    fun `short names do not typo-match`() {
        // "Din" is under the typo-length floor; it must not snap to "Dining".
        val result = sanitizeCategorySuggestion(raw(name = "Din", type = "expenses"), categories)
        assertEquals("Din", (result as CategorySuggestion.New).name)
    }
}
