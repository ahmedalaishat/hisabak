package com.hisabak.feature.brand.domain

import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@JvmInline
value class BrandId(val value: String) {
    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): BrandId = BrandId(Uuid.random().toString())
    }
}
