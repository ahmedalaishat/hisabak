package com.hisabak.feature.user.domain

import com.hisabak.core.common.Currency

data class UserProfile(
    val id: String,
    val name: String,
    val email: String,
    val defaultCurrency: Currency,
) {
    init {
        require(name.isNotBlank()) { "Name must not be blank" }
        require(email.contains('@')) { "Email must be valid" }
    }
}
