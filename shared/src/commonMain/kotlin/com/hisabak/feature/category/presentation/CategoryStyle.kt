package com.hisabak.feature.category.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.hisabak.feature.category.domain.CategoryVocabulary
import com.hisabak.ui.theme.HisabakTheme

/**
 * Shared color and icon vocabulary for Category presentation.
 * Categories store color/icon as short keys (e.g. "green", "cart"); this maps
 * them to Compose primitives. Unknown keys fall back to a neutral default.
 */
object CategoryStyle {
    val palette: List<String> = CategoryVocabulary.colors

    val icons: List<String> = CategoryVocabulary.icons

    @Composable
    fun color(key: String?): Color {
        val c = HisabakTheme.colors
        return when (key) {
            "green"  -> c.catGreen
            "blue"   -> c.catBlue
            "orange" -> c.catOrange
            "red"    -> c.catRed
            "teal"   -> c.catTeal
            "purple" -> c.catPurple
            "pink"   -> c.catPink
            else     -> c.catGray
        }
    }
}
