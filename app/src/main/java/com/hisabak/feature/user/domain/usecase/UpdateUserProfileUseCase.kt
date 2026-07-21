package com.hisabak.feature.user.domain.usecase

import com.hisabak.core.common.DomainResult
import com.hisabak.feature.user.domain.UserProfile
import com.hisabak.feature.user.domain.UserRepository

class UpdateUserProfileUseCase(
    private val repository: UserRepository,
) {
    suspend operator fun invoke(profile: UserProfile): DomainResult<Unit> =
        repository.update(profile)
}
