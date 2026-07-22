package com.hisabak.core.data.local

import com.hisabak.feature.category.domain.CategoryType
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class StarterDataTest {

    private val starters = StarterData()

    @Test
    fun `covers every category type so any transaction can be recorded on first run`() {
        val typesCovered = starters.categories.map { it.type }.toSet()
        assertEquals(CategoryType.entries.toSet(), typesCovered)
    }

    @Test
    fun `category names are non-blank and unique`() {
        val names = starters.categories.map { it.name }
        assertTrue(names.all { it.isNotBlank() })
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `colors and icons stay within the design system palette`() {
        val colors = setOf("green", "blue", "orange", "red", "teal", "purple", "pink", "gray")
        val icons = setOf(
            "wallet", "cart", "briefcase", "car", "utensils", "piggy-bank",
            "home", "film", "book", "heart", "gift", "plane",
        )
        assertTrue(starters.categories.all { it.color in colors })
        assertTrue(starters.categories.all { it.icon in icons })
    }
}
