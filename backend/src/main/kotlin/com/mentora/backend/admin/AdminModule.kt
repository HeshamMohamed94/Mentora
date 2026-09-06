package com.mentora.backend.admin

import com.mentora.backend.admin.repository.AdminRepository
import com.mentora.backend.admin.service.AdminService
import org.koin.dsl.module

val adminModule = module {
    single { AdminRepository(get()) }
    single { AdminService(get()) }
}
