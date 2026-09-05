package com.mentora.backend.progress

import com.mentora.backend.progress.repository.ProgressRepository
import com.mentora.backend.progress.service.ProgressService
import org.koin.dsl.module

val progressModule = module {
    single { ProgressRepository(get()) }
    single { ProgressService(get(), get(), get()) }
}
