package com.hisabak.feature.brand.data.local

import com.hisabak.feature.brand.domain.Brand
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.category.domain.CategoryId

fun BrandEntity.toDomain(): Brand = Brand(
    id = BrandId(id),
    name = name,
    categoryId = categoryId?.let(::CategoryId),
)

fun Brand.toEntity(): BrandEntity = BrandEntity(
    id = id.value,
    name = name,
    categoryId = categoryId?.value,
)
