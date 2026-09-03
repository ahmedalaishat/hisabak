package com.hisabak.feature.category.domain

import com.hisabak.ui.icons.categoryIconVector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategoryIconCatalogTest {

    /**
     * These keys shipped in v1 and are persisted on existing rows and in backups. Dropping or
     * renaming one silently turns those categories into blank circles.
     */
    private val legacyKeys = listOf(
        "wallet", "cart", "briefcase", "car", "utensils", "piggy-bank",
        "home", "film", "book", "heart", "gift", "plane",
    )

    @Test
    fun `every legacy key survives`() {
        val keys = CategoryIconCatalog.entries.map { it.key }
        legacyKeys.forEach { assertTrue(it in keys, "lost legacy icon key: $it") }
    }

    @Test
    fun `keys are unique`() {
        val keys = CategoryIconCatalog.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `every catalogue key resolves to a vector`() {
        CategoryIconCatalog.entries.forEach {
            assertNotNull(categoryIconVector(it.key), "no vector for ${it.key}")
        }
    }

    @Test
    fun `unknown and null keys resolve to nothing so callers can fall back`() {
        assertNull(categoryIconVector("not-a-real-icon"))
        assertNull(categoryIconVector(null))
    }

    @Test
    fun `every icon carries search keywords in both languages`() {
        CategoryIconCatalog.entries.forEach { entry ->
            assertTrue(entry.keywords.isNotEmpty(), "${entry.key} has no keywords")
            assertTrue(
                entry.keywords.any { term -> term.any { it in 'a'..'z' } },
                "${entry.key} has no English keyword",
            )
            assertTrue(
                entry.keywords.any { term -> term.any { it.code in 0x0600..0x06FF } },
                "${entry.key} has no Arabic keyword",
            )
        }
    }

    @Test
    fun `the domain key list matches the catalogue`() {
        assertEquals(CategoryIconCatalog.keys, CategoryVocabulary.icons)
    }

    @Test
    fun `the AI vocabulary is a subset of what the picker offers`() {
        CategoryVocabulary.aiIcons.forEach {
            assertTrue(it in CategoryVocabulary.icons, "AI may suggest unpickable icon: $it")
        }
    }
}
