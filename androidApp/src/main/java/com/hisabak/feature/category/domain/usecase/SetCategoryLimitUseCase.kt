package com.hisabak.feature.category.domain.usecase

import com.hisabak.core.common.Clock
import com.hisabak.core.common.Money
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryLimitRepository
import java.time.YearMonth

/**
 * Sets (or clears, with a `null` [amount]) a category's monthly limit effective from the
 * current month onward. Past months are left untouched.
 */
class SetCategoryLimitUseCase(
    private val repository: CategoryLimitRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(categoryId: CategoryId, amount: Money?) {
        repository.setLimit(categoryId, amount, YearMonth.from(clock.today()))
    }
}
