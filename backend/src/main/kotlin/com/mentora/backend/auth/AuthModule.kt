package com.mentora.backend.auth

import com.mentora.backend.auth.repository.AuthRepository
import com.mentora.backend.auth.service.AuthService
import com.mentora.backend.auth.service.TokenIssuer
import org.koin.dsl.module

val authModule = module {
    single { AuthRepository(get()) }
    single { TokenIssuer(get()) }
    single { AuthService(get(), get(), get()) }
}
