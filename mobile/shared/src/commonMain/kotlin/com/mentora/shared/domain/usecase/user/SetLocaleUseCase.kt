package com.mentora.shared.domain.usecase.user

import com.mentora.shared.auth.AuthState
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.auth.AuthRepository
import com.mentora.shared.data.repository.user.UserRepository
import com.mentora.shared.settings.AppLocale
import com.mentora.shared.settings.PreferenceStore

/**
 * Changes the active UI locale. Always writes [PreferenceStore] immediately regardless of auth
 * state — a guest can still set a UI-language preference locally with no account to persist it
 * against — and, only when [AuthRepository.authState] is currently
 * [AuthState.Authenticated], additionally `PATCH`es `preferredLocale` server-side so the choice
 * survives a future login on another device. A locale change never touches [AuthState]/tokens —
 * it is an orthogonal concern (`execution/PHASE_3_KMP_PLAN.md` Task 6).
 *
 * [onLogin]/[onRegister] implement the plan's two explicit precedence rules for the OTHER
 * direction — reconciling the account's server-side value against whatever was already sitting in
 * [PreferenceStore] before that account's session existed:
 *  - **login overwrites local**: an existing account's own `preferredLocale` is its user's actual
 *    prior choice and wins over anything the local device happened to have (e.g. a guest who
 *    poked around in the language switcher before signing in).
 *  - **register seeds account**: a brand-new account has no `preferredLocale` of its own yet, so
 *    the local value already showing on screen becomes that account's initial value instead of
 *    silently starting from nothing.
 *
 * [com.mentora.shared.domain.usecase.auth.LoginUseCase]/`RegisterUseCase` call [onLogin]/
 * [onRegister] themselves right after a successful login/register — see those classes' kdoc.
 * `onRegister`'s result is deliberately best-effort there: a failure to seed the new account's
 * locale is never surfaced as a registration failure.
 */
class SetLocaleUseCase(
    private val preferenceStore: PreferenceStore,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(locale: AppLocale): ApiResult<Unit> {
        preferenceStore.setLocale(locale)
        if (authRepository.authState.value !is AuthState.Authenticated) return ApiResult.Success(Unit)
        return when (val result = userRepository.updateProfile(preferredLocale = locale.wireValue)) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> result
        }
    }

    /**
     * Applies "login overwrites local": [accountPreferredLocale] is the value already returned by
     * the login response/profile fetch, so this is a pure local [PreferenceStore] write with no
     * further network call. A `null`/absent value (an account with no `preferredLocale` set at
     * all) leaves the current local value untouched rather than clobbering it with nothing.
     */
    fun onLogin(accountPreferredLocale: String?) {
        val locale = accountPreferredLocale?.let(AppLocale::fromWireValue) ?: return
        preferenceStore.setLocale(locale)
    }

    /**
     * Applies "register seeds account": a freshly-registered account has no `preferredLocale` of
     * its own yet, so the CURRENT local [PreferenceStore] value is sent up via `PATCH /users/me`
     * to become that account's initial value.
     */
    suspend fun onRegister(): ApiResult<Unit> =
        when (val result = userRepository.updateProfile(preferredLocale = preferenceStore.locale.value.wireValue)) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> result
        }
}
