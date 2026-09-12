package com.mentora.shared.di

import com.mentora.shared.auth.AuthTokens
import com.mentora.shared.auth.SessionManager
import com.mentora.shared.auth.TokenStorage
import com.mentora.shared.auth.installAuthInterception
import com.mentora.shared.config.ApiEnvironment
import com.mentora.shared.data.network.ApiClient
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.HttpClientFactory
import com.mentora.shared.data.network.dto.RefreshRequestDto
import com.mentora.shared.data.network.dto.RefreshResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Bundles the three interdependent networking singletons that MUST be constructed together, in
 * this exact order, per `execution/PHASE_3_KMP_PLAN.md` Task 5 (the same wiring shape every
 * `commonTest` repository test already hand-builds — see e.g. `AuthRepositoryImplTest`'s
 * `repositoryFor` helper):
 *
 *  1. [httpClient] — built by [HttpClientFactory] (base URL/timeouts/retry/CSRF, no cookie jar).
 *  2. [apiClient] — wraps [httpClient]; captured by [SessionManager]'s `refreshTokens` lambda so a
 *     single-flight refresh can call `POST /auth/refresh` through the SAME configured client.
 *  3. [sessionManager] — depends on (2) for its refresh lambda; [httpClient] then has
 *     [installAuthInterception] installed onto it (in place — see that function's kdoc) with this
 *     SAME [sessionManager], closing the loop: every ordinary request now attaches this session's
 *     bearer token and retries through this session's single-flight refresh on a 401.
 *
 * A private, non-DI-shaped holder (not itself resolved by anything) — the 3 `single { get<...>() }`
 * declarations in [networkModule] are what Koin callers actually depend on.
 */
private class NetworkBundle(
    val httpClient: HttpClient,
    val apiClient: ApiClient,
    val sessionManager: SessionManager,
)

private fun buildNetworkBundle(
    environment: ApiEnvironment,
    engine: HttpClientEngine,
    tokenStorage: TokenStorage,
    enableLogging: Boolean,
): NetworkBundle {
    val httpClient = HttpClientFactory.create(engine, environment, enableLogging)
    val apiClient = ApiClient(httpClient)
    val sessionManager = SessionManager(tokenStorage) { refreshToken ->
        when (
            val result = apiClient.post<RefreshRequestDto, RefreshResponseDto>(
                "/api/v1/auth/refresh",
                RefreshRequestDto(refreshToken),
            )
        ) {
            is ApiResult.Success -> ApiResult.Success(AuthTokens(result.data.accessToken, result.data.refreshToken))
            is ApiResult.Failure -> result
        }
    }
    httpClient.installAuthInterception(sessionManager)
    return NetworkBundle(httpClient, apiClient, sessionManager)
}

/**
 * [environment] is provided as a `single` here so every use case that needs it
 * ([com.mentora.shared.domain.usecase.media.GetLessonPlaybackSourceUseCase],
 * [com.mentora.shared.domain.usecase.media.ResolveThumbnailUrlUseCase]) resolves the exact same
 * instance `initKoin` was called with.
 *
 * [HttpClientEngine] and [TokenStorage] are deliberately NOT provided here — they come from the
 * platform module ([initKoin]'s `platformModule` parameter), per
 * `execution/PHASE_3_KMP_PLAN.md` Task 15: "the platform module supplies TokenStorage/
 * PreferenceStore's actual Android/iOS implementations, and the underlying Ktor HttpClientEngine."
 * [PreferenceStore] is also platform-supplied but has no networking dependency of its own, so it
 * is declared as a platform-module binding only, not referenced in this file.
 */
internal fun networkModule(environment: ApiEnvironment, enableNetworkLogging: Boolean): Module = module {
    single { environment }
    single { buildNetworkBundle(get(), get(), get(), enableNetworkLogging) }
    single { get<NetworkBundle>().httpClient }
    single { get<NetworkBundle>().apiClient }
    // The one and only `SessionManager` for the whole app graph — MUST be `single`, never
    // `factory`: it owns the in-memory access-token cache and the `authState` StateFlow every
    // screen observes, both of which must be the same shared instance everywhere it's injected.
    single { get<NetworkBundle>().sessionManager }
}
