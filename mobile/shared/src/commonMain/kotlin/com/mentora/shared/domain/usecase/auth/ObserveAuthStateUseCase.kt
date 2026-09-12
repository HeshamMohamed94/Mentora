package com.mentora.shared.domain.usecase.auth

import com.mentora.shared.auth.AuthState
import com.mentora.shared.data.repository.auth.AuthRepository
import kotlinx.coroutines.flow.StateFlow

class ObserveAuthStateUseCase(private val repository: AuthRepository) {
    operator fun invoke(): StateFlow<AuthState> = repository.authState
}
