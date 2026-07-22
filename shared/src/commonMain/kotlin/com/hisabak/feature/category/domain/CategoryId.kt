package com.hisabak.feature.category.domain

import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@JvmInline
value class CategoryId(val value: String) {
    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): CategoryId = CategoryId(Uuid.random().toString())
    }
}
