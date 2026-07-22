package com.hisabak.feature.budget.domain

import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@JvmInline
value class BudgetId(val value: String) {
    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): BudgetId = BudgetId(Uuid.random().toString())
    }
}
