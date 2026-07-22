package com.hisabak.feature.budget.domain

import java.util.UUID

@JvmInline
value class BudgetId(val value: String) {
    companion object {
        fun new(): BudgetId = BudgetId(UUID.randomUUID().toString())
    }
}
