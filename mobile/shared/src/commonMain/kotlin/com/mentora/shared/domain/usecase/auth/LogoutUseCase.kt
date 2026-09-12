package com.mentora.shared.domain.usecase.auth

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.auth.AuthRepository

class LogoutUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(): ApiResult<Unit> = repository.logout()
}
