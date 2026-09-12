package com.mentora.shared.facade

import com.mentora.shared.domain.usecase.auth.LoginUseCase
import com.mentora.shared.domain.usecase.auth.LogoutUseCase
import com.mentora.shared.domain.usecase.auth.ObserveAuthStateUseCase
import com.mentora.shared.domain.usecase.auth.RefreshSessionUseCase
import com.mentora.shared.domain.usecase.auth.RegisterUseCase
import com.mentora.shared.domain.usecase.auth.RestoreSessionUseCase
import org.koin.core.Koin

/**
 * Task 5's auth domain, resolved once and exposed as callable use-case properties — e.g.
 * `sdk.auth.login(email, password)` (every use case below is `operator fun invoke(...)`, see each
 * one's own kdoc for its exact signature/return type). Never exposes [AuthRepository]
 * [com.mentora.shared.data.repository.auth.AuthRepository] or anything network-shaped — see
 * [com.mentora.shared.MentoraSdk]'s kdoc for the full façade boundary rule.
 */
class AuthFacade internal constructor(koin: Koin) {
    val register: RegisterUseCase = koin.get()
    val login: LoginUseCase = koin.get()
    val logout: LogoutUseCase = koin.get()
    val observeAuthState: ObserveAuthStateUseCase = koin.get()
    val refreshSession: RefreshSessionUseCase = koin.get()
    val restoreSession: RestoreSessionUseCase = koin.get()
}
