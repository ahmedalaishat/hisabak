package com.hisabak.core.common

sealed class DomainError(open val message: String) {
    data class NotFound(val entity: String, val id: String) :
        DomainError("$entity not found: $id")

    data class ValidationFailed(override val message: String) : DomainError(message)

    data class Conflict(override val message: String) : DomainError(message)

    data class Unexpected(val cause: Throwable) :
        DomainError(cause.message ?: "Unexpected error")
}
