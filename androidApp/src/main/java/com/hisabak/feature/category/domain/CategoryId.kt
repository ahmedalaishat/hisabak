package com.hisabak.feature.category.domain

import java.util.UUID

@JvmInline
value class CategoryId(val value: String) {
    companion object {
        fun new(): CategoryId = CategoryId(UUID.randomUUID().toString())
    }
}
