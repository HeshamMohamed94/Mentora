package com.mentora.backend.aitutor

import com.mentora.backend.aitutor.provider.AiProvider
import com.mentora.backend.aitutor.provider.AnthropicAiProvider
import com.mentora.backend.aitutor.provider.StubAiProvider
import com.mentora.backend.aitutor.repository.AiTutorRepository
import com.mentora.backend.aitutor.service.AiTutorService
import com.mentora.backend.config.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import org.koin.dsl.module
import org.koin.ktor.ext.get

/**
 * [aiProviderOverride] is a test-only seam (PHASE_6_SYSTEM_DESIGN.md § 20.3) — null in production.
 * When no override is given, the binding falls back to [StubAiProvider] unless
 * [AppConfig.aiProviderMode] is `"anthropic"`, in which case it binds the real
 * [AnthropicAiProvider] over a single long-lived [HttpClient] (Design § 19.2).
 *
 * The [HttpClient] is only registered — and therefore only ever created — when actually needed
 * (no override, anthropic mode), so stub-mode startup never allocates an HTTP client it will never
 * use.
 */
fun aiTutorModule(appConfig: AppConfig, aiProviderOverride: AiProvider? = null) = module {
    if (aiProviderOverride == null && appConfig.aiProviderMode() == "anthropic") {
        single { anthropicHttpClient(appConfig) }
    }
    single<AiProvider> {
        aiProviderOverride
            ?: if (appConfig.aiProviderMode() == "anthropic") {
                AnthropicAiProvider(
                    httpClient = get(),
                    apiKey = requireNotNull(appConfig.aiProviderApiKey),
                    model = appConfig.aiProviderModel,
                )
            } else {
                StubAiProvider()
            }
    }
    single { AiTutorRepository(get()) }
    single { AiTutorService(get(), get(), get(), get(), get()) }
}

private fun anthropicHttpClient(appConfig: AppConfig): HttpClient = HttpClient(CIO) {
    expectSuccess = false // non-2xx responses are mapped ourselves (PHASE_6_SYSTEM_DESIGN.md § 11)
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 30_000
        requestTimeoutMillis = appConfig.aiProviderTimeoutSeconds * 1000L
    }
}

/**
 * Closes the Anthropic [HttpClient] on shutdown — mirrors `plugins/Database.kt`'s
 * `monitor.subscribe(ApplicationStopping) { get<MongoClient>().close() }` pattern exactly. Only
 * subscribes when the client was actually registered (same condition as [aiTutorModule]'s
 * conditional `single`), so shutdown never instantiates an HttpClient purely to close it.
 */
fun Application.configureAiTutorHttpClientLifecycle(appConfig: AppConfig, aiProviderOverride: AiProvider? = null) {
    if (aiProviderOverride == null && appConfig.aiProviderMode() == "anthropic") {
        monitor.subscribe(ApplicationStopping) {
            get<HttpClient>().close()
        }
    }
}
