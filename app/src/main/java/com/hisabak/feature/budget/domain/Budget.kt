package com.hisabak.feature.budget.domain

import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.core.common.Money
import java.time.LocalDate

data class Budget(
    val id: BudgetId,
    val name: String,
    val amount: Money,
    val startAt: LocalDate,
    val endAt: LocalDate?,
    val saving: Boolean = false,
    val period: Int = 1,
    val reoccurrence: Reoccurrence,
    val categoryIds: Set<CategoryId>,
) {
    init {
        require(name.isNotBlank()) { "Budget name must not be blank" }
        require(period >= 1) { "Budget period must be >= 1" }
        if (reoccurrence == Reoccurrence.CUSTOM) {
            requireNotNull(endAt) { "CUSTOM budgets require an endAt date" }
            require(!endAt.isBefore(startAt)) { "endAt must not precede startAt" }
        }
        require(amount.isPositive) { "Budget amount must be positive" }
    }
}
