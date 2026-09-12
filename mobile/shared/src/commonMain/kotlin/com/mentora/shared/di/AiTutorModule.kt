package com.mentora.shared.di

import com.mentora.shared.data.repository.aitutor.AiTutorRepository
import com.mentora.shared.data.repository.aitutor.AiTutorRepositoryImpl
import com.mentora.shared.domain.usecase.aitutor.GetAiConversationUseCase
import com.mentora.shared.domain.usecase.aitutor.SendAiTutorMessageUseCase
import org.koin.dsl.module

/**
 * Task 14's AI Tutor domain. [AiTutorRepositoryImpl] takes both [com.mentora.shared.data.network.ApiClient]
 * (the paged-conversation GET) and the raw [io.ktor.client.HttpClient] (the streamed-send POST,
 * which bypasses `ApiClient` on purpose per Decision D-E) — both resolve to the SAME underlying
 * client instance [networkModule] builds, since `ApiClient` only ever wraps that one `HttpClient`.
 */
internal val aiTutorModule = module {
    single<AiTutorRepository> { AiTutorRepositoryImpl(get(), get()) }

    factory { GetAiConversationUseCase(get()) }
    factory { SendAiTutorMessageUseCase(get()) }
}
