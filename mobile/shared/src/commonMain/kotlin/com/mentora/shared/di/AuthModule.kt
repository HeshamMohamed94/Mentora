package com.mentora.shared.di

import com.mentora.shared.data.repository.auth.AuthRepository
import com.mentora.shared.data.repository.auth.AuthRepositoryImpl
import com.mentora.shared.domain.usecase.auth.LoginUseCase
import com.mentora.shared.domain.usecase.auth.LogoutUseCase
import com.mentora.shared.domain.usecase.auth.ObserveAuthStateUseCase
import com.mentora.shared.domain.usecase.auth.RefreshSessionUseCase
import com.mentora.shared.domain.usecase.auth.RegisterUseCase
import com.mentora.shared.domain.usecase.auth.RestoreSessionUseCase
import org.koin.dsl.module

/**
 * Task 5's auth domain. [AuthRepositoryImpl] is `single` (not `factory`) purely because it is
 * stateless and cheap to share — unlike [com.mentora.shared.auth.SessionManager] (declared in
 * [networkModule]), sharing it is not a correctness requirement, only a minor allocation
 * saving; every repository in this file follows the same convention for the same reason.
 * Every use case below is `factory`, per `execution/PHASE_3_KMP_PLAN.md` Task 15 AC.
 */
internal val authModule = module {
    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get()) }

    factory { RegisterUseCase(get(), get()) }
    factory { LoginUseCase(get(), get()) }
    factory { LogoutUseCase(get()) }
    factory { ObserveAuthStateUseCase(get()) }
    factory { RefreshSessionUseCase(get()) }
    factory { RestoreSessionUseCase(get()) }
}
