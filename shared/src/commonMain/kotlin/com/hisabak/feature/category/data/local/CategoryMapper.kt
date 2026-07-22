package com.hisabak.feature.category.data.local

import com.hisabak.feature.category.domain.Category
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryType

fun CategoryEntity.toDomain(): Category = Category(
    id = CategoryId(id),
    name = name,
    type = CategoryType.valueOf(type),
    color = color,
    icon = icon,
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id.value,
    name = name,
    type = type.name,
    color = color,
    icon = icon,
)
