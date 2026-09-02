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

    /** Every icon the picker offers — and everything a stored key is validated against. */
    val icons: List<String> = CategoryIconCatalog.keys

    /**
     * The subset the on-device models are asked to choose from. Deliberately small: the icon list
     * is inlined into the prompt, and a 144-way choice both bloats it and costs accuracy on a
     * model this size. Users reach the rest through the picker.
     */
    val aiIcons: List<String> = listOf(
        "wallet", "cart", "briefcase", "car", "utensils", "piggy-bank",
        "home", "film", "book", "heart", "gift", "plane",
    )
}
