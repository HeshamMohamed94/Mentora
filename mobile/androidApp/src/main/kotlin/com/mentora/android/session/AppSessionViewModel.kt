package com.mentora.android.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mentora.shared.MentoraSdk
import com.mentora.shared.auth.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * T4 Part B — pure session-state plumbing that a later task's real UI (Login/Register/Home) will
 * collect. Builds NO screen of its own; `MainActivity`'s current call site is a temporary
 * smoke-test `Text`, not a real screen.
 *
 * Session restoration is no longer this class's concern: `MentoraApplication.onCreate()` now owns
 * the single, application-scoped, ordered bootstrap sequence (`restoreSession()` once, then locale
 * seeding — see that class's kdoc). This ViewModel only:
 * 1. Subscribes to [MentoraSdk.auth]'s `observeAuthState` [StateFlow] (starts at
 *    [AuthState.Unknown] until the application-owned `restoreSession()` resolves it).
 * 2. Whenever the observed state becomes [AuthState.Authenticated] with a `null` user (legitimate
 *    right after a cold-start token restore — `TokenStorage` carries only tokens, never identity),
 *    follows up automatically with `sdk.user.getProfile()`. `GetProfileUseCase`'s own kdoc documents
 *    that a successful call updates `SessionManager`'s current `Authenticated` user as a side
 *    effect, so the follow-up `Authenticated(user = non-null)` value arrives back through the same
 *    `observeAuthState` [StateFlow] — no separate merge step is needed here.
 *
 * Because `StateFlow` only emits on actual value change (not per collector-attach), a
 * `getProfile()` failure (e.g. an invalid/expired token slipping past `restoreSession()`) triggers
 * this follow-up exactly once rather than looping — the state simply stays at
 * `Authenticated(user = null)` until whatever later action (e.g. a forced re-login) changes it.
 * This ViewModel is Activity-scoped, so a second instance re-triggering `getProfile()` on an
 * already-`Authenticated(user=null)` state is harmless/idempotent — not a race worth engineering
 * around, since `observeAuthState()` is a safe, multi-observer `StateFlow` read.
 */
class AppSessionViewModel(private val sdk: MentoraSdk) : ViewModel() {

    private val _sessionState = MutableStateFlow<AuthState>(AuthState.Unknown)
    val sessionState: StateFlow<AuthState> = _sessionState.asStateFlow()

    init {
        viewModelScope.launch {
            sdk.auth.observeAuthState().collect { state ->
                _sessionState.value = state
                if (state is AuthState.Authenticated && state.user == null) {
                    sdk.user.getProfile()
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            sdk.auth.logout()
        }
    }

    /** Plain [ViewModelProvider.Factory] — this app layers no other DI framework on top of
     * `shared`'s own Koin graph (see `MentoraApplication`'s kdoc), so [MentoraSdk] is threaded
     * through by hand rather than via `koinViewModel()`. */
    class Factory(private val sdk: MentoraSdk) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppSessionViewModel(sdk) as T
    }
}
