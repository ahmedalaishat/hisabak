package com.hisabak.feature.category.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.hisabak.feature.category.domain.CategoryColor
import com.hisabak.feature.category.domain.CategoryVocabulary
import com.hisabak.ui.theme.HisabakTheme
import com.hisabak.ui.theme.hueForegroundDark
import com.hisabak.ui.theme.hueForegroundLight

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
        CategoryColor.hueOf(key)?.let { hue ->
            return if (HisabakTheme.isDark) hueForegroundDark(hue) else hueForegroundLight(hue)
        }
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
