package com.hisabak.feature.brand.domain

import java.util.UUID

@JvmInline
value class BrandId(val value: String) {
    companion object {
        fun new(): BrandId = BrandId(UUID.randomUUID().toString())
    }
}
