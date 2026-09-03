package com.hisabak.feature.category.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CategoryIconSearchTest {

    private fun keys(query: String) = searchCategoryIcons(query).map { it.key }

    @Test
    fun `a blank query returns the whole catalogue in order`() {
        assertEquals(CategoryIconCatalog.entries, searchCategoryIcons(""))
        assertEquals(CategoryIconCatalog.entries, searchCategoryIcons("   "))
    }

    @Test
    fun `English keywords match`() {
        assertTrue("coffee" in keys("cafe"))
        assertTrue("fuel" in keys("petrol"))
        assertTrue("gym" in keys("workout"))
    }

    @Test
    fun `Arabic keywords match`() {
        assertTrue("utensils" in keys("مطعم"))
        assertTrue("fuel" in keys("بنزين"))
        assertTrue("charity" in keys("زكاة"))
    }

    @Test
    fun `Arabic matches without hamza or diacritics`() {
        assertTrue("utensils" in keys("اكل"), "bare alef should match أكل")
        assertTrue("plane" in keys("طيــران"), "tatweel should be ignored")
    }

    @Test
    fun `search is case and whitespace insensitive`() {
        assertTrue("hotel" in keys("  HOTEL  "))
    }

    @Test
    fun `the key itself is searchable`() {
        assertTrue("ice-cream" in keys("ice-cream"))
    }

    @Test
    fun `prefix matches rank above mid-word ones`() {
        // "car" is a prefix of the car keyword and appears mid-word in "e-scooter"/"card".
        val results = keys("car")
        assertTrue(results.isNotEmpty())
        assertEquals("car", results.first())
    }

    @Test
    fun `a query that matches nothing returns empty`() {
        assertTrue(searchCategoryIcons("zzzzqqq").isEmpty())
    }

    // ── Deriving an icon from a category's name ───────────────────────────────

    @Test
    fun `a single-word name resolves directly`() {
        assertEquals("coffee", iconForCategoryName("Coffee"))
        assertEquals("hotel", iconForCategoryName("Hotel"))
    }

    @Test
    fun `a multi-word name falls back to its most specific word`() {
        // The whole string matches no keyword as a unit — the split is what saves it.
        assertTrue(searchCategoryIcons("Coffee shops").isEmpty(), "precondition: whole-string match fails")
        assertEquals("coffee", iconForCategoryName("Coffee shops"))
        assertEquals("family", iconForCategoryName("Family support"))
    }

    @Test
    fun `Arabic names resolve too`() {
        assertEquals("utensils", iconForCategoryName("مطعم"))
    }

    @Test
    fun `a name that means nothing to the catalogue resolves to nothing`() {
        assertEquals(null, iconForCategoryName("Zzzq"))
        assertEquals(null, iconForCategoryName(""))
        assertEquals(null, iconForCategoryName("   "))
    }

    @Test
    fun `the picker seed is empty when nothing matches so it opens on the full list`() {
        assertEquals("", iconSearchSeed("Zzzq"))
        assertEquals("", iconSearchSeed(""))
        assertEquals("Coffee", iconSearchSeed("Coffee"))
    }

    @Test
    fun `short words are ignored when splitting a name`() {
        // "of"/"my" carry no meaning and would match far too loosely.
        assertEquals(null, iconForCategoryName("of my"))
    }
}
