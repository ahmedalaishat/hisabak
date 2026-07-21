package com.hisabak.feature.brand.domain

import com.hisabak.feature.category.domain.CategoryId

data class Brand(
    val id: BrandId,
    val name: String,
    val categoryId: CategoryId?,
) {
    init {
        require(name.isNotBlank()) { "Brand name must not be blank" }
    }
}
