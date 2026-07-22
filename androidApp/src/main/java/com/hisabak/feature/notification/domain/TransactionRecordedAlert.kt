package com.hisabak.feature.notification.domain

/**
 * Heads-up confirmation that an SMS-imported transaction was saved. Posted to the OS only (not
 * kept in the in-app list — it's a transient confirmation). The icon/color keys drive the
 * category glyph tile; [categoryId] vs [brandId] decides where a tap lands.
 */
data class TransactionRecordedAlert(
    val transactionId: String,
    val title: String,
    val message: String,
    /** Set when the brand is categorized — a tap focuses this category on the dashboard. */
    val categoryId: String?,
    /** When [categoryId] is null (uncategorized), a tap opens this brand's editor to categorize it. */
    val brandId: String,
    /** Category icon key (e.g. "cart"); null → no category, the app icon is shown. */
    val iconKey: String?,
    /** Category color key (e.g. "teal"); null → neutral tile. */
    val colorKey: String?,
)
