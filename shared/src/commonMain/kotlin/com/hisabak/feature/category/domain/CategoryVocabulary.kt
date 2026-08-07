package com.hisabak.feature.category.domain

/**
 * The category color/icon key vocabulary. Domain-level because Category persists these keys and
 * the AI suggestion layer must validate model output against them; presentation maps them to
 * Compose primitives (CategoryStyle).
 */
object CategoryVocabulary {
    val colors: List<String> = listOf(
        "green", "blue", "orange", "red", "teal", "purple", "pink", "gray",
    )

    val icons: List<String> = listOf(
        "wallet", "cart", "briefcase", "car", "utensils", "piggy-bank",
        "home", "film", "book", "heart", "gift", "plane",
    )
}
