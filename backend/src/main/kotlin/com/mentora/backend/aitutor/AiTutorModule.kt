package com.mentora.backend.aitutor

import com.mentora.backend.aitutor.provider.AiProvider
import com.mentora.backend.aitutor.provider.StubAiProvider
import com.mentora.backend.aitutor.repository.AiTutorRepository
import com.mentora.backend.aitutor.service.AiTutorService
import org.koin.dsl.module

/**
 * [aiProviderOverride] is a test-only seam (PHASE_6_SYSTEM_DESIGN.md § 20.3) — null in production,
 * where the binding falls back to [StubAiProvider]. The real Anthropic-backed selection
 * (`aiProviderMode() == "anthropic"`) is wired in a later task once `AnthropicAiProvider` exists.
 */
fun aiTutorModule(aiProviderOverride: AiProvider? = null) = module {
    single<AiProvider> { aiProviderOverride ?: StubAiProvider() }
    single { AiTutorRepository(get()) }
    single { AiTutorService(get(), get(), get(), get(), get()) }
}
