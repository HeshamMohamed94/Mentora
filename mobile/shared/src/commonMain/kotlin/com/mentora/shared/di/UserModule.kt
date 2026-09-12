package com.mentora.shared.di

import com.mentora.shared.data.repository.user.UserRepository
import com.mentora.shared.data.repository.user.UserRepositoryImpl
import com.mentora.shared.domain.usecase.user.GetProfileUseCase
import com.mentora.shared.domain.usecase.user.ObserveLocaleUseCase
import com.mentora.shared.domain.usecase.user.SetLocaleUseCase
import com.mentora.shared.domain.usecase.user.SetThemeUseCase
import com.mentora.shared.domain.usecase.user.UpdateProfileUseCase
import org.koin.dsl.module

/** Task 6's user-profile/preference domain. See [authModule]'s kdoc for the single-vs-factory convention. */
internal val userModule = module {
    single<UserRepository> { UserRepositoryImpl(get(), get()) }

    factory { GetProfileUseCase(get()) }
    factory { UpdateProfileUseCase(get()) }
    factory { ObserveLocaleUseCase(get()) }
    // Depends on AuthRepository (for authState) — resolved fine regardless of authModule's
    // declaration order, since Koin resolves the dependency graph lazily at `get()` time, not at
    // module-declaration time.
    factory { SetLocaleUseCase(get(), get(), get()) }
    factory { SetThemeUseCase(get()) }
}
