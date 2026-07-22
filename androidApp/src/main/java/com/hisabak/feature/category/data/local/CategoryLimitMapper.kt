package com.hisabak.feature.category.data.local

import com.hisabak.core.common.Currency
import com.hisabak.core.common.Money
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryLimit
import java.time.YearMonth

fun YearMonth.toEncoded(): Int = year * 100 + monthValue

fun Int.toYearMonth(): YearMonth = YearMonth.of(this / 100, this % 100)

fun CategoryLimitEntity.toDomain(): CategoryLimit = CategoryLimit(
    categoryId = CategoryId(categoryId),
    amount = amountMinor?.let { Money(it, Currency(currency)) },
    effectiveFrom = effectiveFrom.toYearMonth(),
)

fun CategoryLimit.toEntity(fallbackCurrency: Currency): CategoryLimitEntity = CategoryLimitEntity(
    categoryId = categoryId.value,
    effectiveFrom = effectiveFrom.toEncoded(),
    amountMinor = amount?.amountMinor,
    currency = (amount?.currency ?: fallbackCurrency).code,
)
