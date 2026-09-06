package com.mentora.backend.aitutor

import com.mentora.backend.aitutor.provider.AiProvider
import com.mentora.backend.aitutor.provider.StubAiProvider
import com.mentora.backend.aitutor.repository.AiTutorRepository
import com.mentora.backend.aitutor.service.AiTutorService
import org.koin.dsl.module

val aiTutorModule = module {
    single<AiProvider> { StubAiProvider() }
    single { AiTutorRepository(get()) }
    single { AiTutorService(get(), get(), get(), get()) }
}
