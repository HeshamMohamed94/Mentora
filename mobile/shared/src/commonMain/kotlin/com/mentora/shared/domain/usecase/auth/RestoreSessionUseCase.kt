package com.mentora.shared.domain.usecase.auth

import com.mentora.shared.auth.AuthState
import com.mentora.shared.data.repository.auth.AuthRepository

/** Reads persisted tokens on app cold-start and sets the initial [AuthState] — see
 * [AuthRepository.restoreSession]. */
class RestoreSessionUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(): AuthState = repository.restoreSession()
}
