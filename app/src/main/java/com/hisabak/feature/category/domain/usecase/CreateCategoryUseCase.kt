package com.hisabak.feature.category.domain.usecase

import com.hisabak.feature.category.domain.Category
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryRepository
import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.core.common.DomainResult

class CreateCategoryUseCase(
    private val repository: CategoryRepository,
) {
    suspend operator fun invoke(
        name: String,
        type: CategoryType,
        color: String = Category.DEFAULT_COLOR,
        icon: String = Category.DEFAULT_ICON,
    ): DomainResult<Category> {
        val category = Category(
            id = CategoryId.new(),
            name = name,
            type = type,
            color = color,
            icon = icon,
        )
        return repository.upsert(category).map { category }
    }
}
