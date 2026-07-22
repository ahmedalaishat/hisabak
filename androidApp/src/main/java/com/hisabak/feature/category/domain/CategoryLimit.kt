package com.hisabak.feature.category.domain

import com.hisabak.core.common.Money
import java.time.YearMonth

/**
 * A monthly spending cap for a category, effective from [effectiveFrom] onward until a later
 * entry supersedes it. [amount] is `null` when the limit was cleared from that month forward.
 * Earlier months keep whatever limit (or none) they had.
 */
data class CategoryLimit(
    val categoryId: CategoryId,
    val amount: Money?,
    val effectiveFrom: YearMonth,
)

/** The limit in effect for [categoryId] during [month], or `null` if none applied by then. */
fun List<CategoryLimit>.effectiveFor(categoryId: CategoryId, month: YearMonth): Money? =
    asSequence()
        .filter { it.categoryId == categoryId && !it.effectiveFrom.isAfter(month) }
        .maxByOrNull { it.effectiveFrom }
        ?.amount
