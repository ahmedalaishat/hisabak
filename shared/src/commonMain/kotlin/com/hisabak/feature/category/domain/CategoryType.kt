package com.hisabak.feature.category.domain

enum class CategoryType {
    INCOME,
    EXPENSES,
    SAVINGS,
    INVESTMENT;

    val isDebit: Boolean get() = this == EXPENSES
    val isCredit: Boolean get() = this == INCOME

    /** Bucket types money moves in and out of — their transactions carry a deposit/withdrawal direction. */
    val hasDirection: Boolean get() = this == SAVINGS || this == INVESTMENT
}
