package com.mentora.shared.domain.usecase.auth

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.auth.AuthRepository

/** Forces an out-of-band session refresh (e.g. on app foreground) — see [AuthRepository.refreshSession]. */
class RefreshSessionUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(): ApiResult<Unit> = repository.refreshSession()
}
