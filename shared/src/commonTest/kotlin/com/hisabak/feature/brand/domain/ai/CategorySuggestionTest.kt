package com.hisabak.feature.brand.domain.ai

import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.testutil.category
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CategorySuggestionTest {

    private val categories = listOf(
        category(id = "c1", name = "Groceries"),
        category(id = "c2", name = "Dining"),
    )

    private fun raw(
        existing: String? = null,
        name: String? = null,
        type: String? = null,
        color: String? = null,
        icon: String? = null,
    ) = AiRawCategorySuggestion(existing, name, type, color, icon)

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
            raw(existing = "Utilities", name = "Utilities", type = "expenses", color = "blue", icon = "home"),
            categories,
        )
        val new = result as CategorySuggestion.New
        assertEquals("Utilities", new.name)
        assertEquals(CategoryType.EXPENSES, new.type)
        assertEquals("blue", new.color)
        assertEquals("home", new.icon)
    }

    @Test
    fun `a new name matching an existing category becomes Existing`() {
        val result = sanitizeCategorySuggestion(
            raw(name = "DINING", type = "expenses", color = "red", icon = "utensils"),
            categories,
        )
        assertEquals("c2", (result as CategorySuggestion.Existing).category.id.value)
    }

    @Test
    fun `invalid type color and icon fall back to defaults`() {
        val result = sanitizeCategorySuggestion(
            raw(name = "Pets", type = "fun", color = "gold", icon = "dog"),
            categories,
        )
        val new = result as CategorySuggestion.New
        assertEquals(CategoryType.EXPENSES, new.type)
        assertEquals("gray", new.color)
        assertEquals("wallet", new.icon)
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
