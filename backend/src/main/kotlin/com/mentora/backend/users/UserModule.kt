package com.mentora.backend.users

import com.mentora.backend.users.repository.UserRepository
import com.mentora.backend.users.service.UserService
import org.koin.dsl.module

val usersModule = module {
    single { UserRepository(get()) }
    single { UserService(get()) }
}
