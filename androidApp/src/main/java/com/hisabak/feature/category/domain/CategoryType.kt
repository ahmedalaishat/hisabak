package com.hisabak.feature.category.domain

enum class CategoryType {
    INCOME,
    EXPENSES,
    SAVINGS,
    INVESTMENT;

    val isDebit: Boolean get() = this == EXPENSES
    val isCredit: Boolean get() = this == INCOME
}
