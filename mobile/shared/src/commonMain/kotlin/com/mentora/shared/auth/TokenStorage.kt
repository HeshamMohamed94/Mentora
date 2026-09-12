package com.mentora.shared.auth

/**
 * The access/refresh token pair issued by `POST /auth/login`, `/auth/register`, and
 * `/auth/refresh`. Secrets only — this type must never be persisted through
 * [com.mentora.shared.settings.PreferenceStore] (that abstraction's `multiplatform-settings`
 * backing is plain, unencrypted storage — fine for non-secret locale/theme values, never for a
 * token). See `architecture/AUTH_SECURITY.md § 4`.
 */
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
)

/**
 * Secure, platform-backed storage for [AuthTokens].
 *
 * Deliberately a plain Kotlin `interface` in `commonMain` rather than a literal `expect`/`actual`
 * pair: Kotlin requires every `actual` counterpart of an `expect class` to share an identical
 * constructor signature, but the two real implementations can't share one — Android's secure
 * implementation needs a platform `Context` (supplied by Koin's `androidContext()` when the
 * platform DI module is wired in Task 15) while iOS's Keychain implementation needs none. A plain
 * common interface with a distinct concrete class per platform (constructed by platform-specific
 * DI, never from `commonMain`) is the only shape that is simultaneously buildable and lets
 * [FakeTokenStorage] (`commonTest`) implement it directly with zero constructor burden. This
 * mirrors how `multiplatform-settings` itself models its own `Settings` type. Still satisfies the
 * architectural intent of `execution/PHASE_3_KMP_PLAN.md` Task 4: the interface lives in
 * `commonMain`, every platform-specific concern (Keystore/DataStore vs. Keychain) lives entirely
 * behind it in `androidMain`/`iosMain`.
 *
 * Every implementation must never log a token value (no `Log.d`/`println`/equivalent — see
 * `architecture/AUTH_SECURITY.md § 4`).
 */
interface TokenStorage {
    suspend fun saveTokens(tokens: AuthTokens)

    suspend fun readTokens(): AuthTokens?

    suspend fun clearTokens()
}
