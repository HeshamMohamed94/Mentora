package com.mentora.android.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mentora.shared.MentoraSdk
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Login's own field values + in-flight/error state. Deliberately has NO per-field error slot —
 * `LoginUseCase` does no local validation, and the server's only realistic failure
 * ([ApiErrorCode.AuthInvalidCredentials]) is a deliberately non-field-specific security design
 * (`ux/SCREEN_UX_SPECS.md § 6`) — see [mapLoginFailure]. */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val generalError: ApiErrorCode? = null,
)

/** The two actionable, field-specific Register failures (`ux/SCREEN_UX_SPECS.md § 7`) — everything
 * else falls through to [RegisterUiState.generalError]. */
sealed interface EmailFieldError {
    /** `fields["email"] == "INVALID"` (client-side [com.mentora.shared.domain.validation.EmailValidator]
     * or the server's own mirror of it). */
    data object Invalid : EmailFieldError

    /** [ApiErrorCode.EmailAlreadyRegistered] — not literally present in `fields`, but routed here per
     * the D51 precedent (`web/src/app/[locale]/(public)/register/register-form.tsx`'s `setError`
     * call): specific/actionable enough to place directly under the Email field, unlike Login's
     * deliberately-vague error. */
    data object AlreadyRegistered : EmailFieldError
}

/** `fields["password"] == "WEAK"` (client-side [com.mentora.shared.domain.validation.PasswordValidator]
 * or the server's own mirror of it) — the only field-specific Password failure that exists. */
sealed interface PasswordFieldError {
    data object Weak : PasswordFieldError
}

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val generalError: ApiErrorCode? = null,
    val emailError: EmailFieldError? = null,
    val passwordError: PasswordFieldError? = null,
)

/**
 * Login's error-routing rule (`ux/SCREEN_UX_SPECS.md § 6`, `product/USER_FLOWS.md § 2`): EVERY
 * failure — including the generic [ApiErrorCode.AuthInvalidCredentials] most login attempts will
 * actually hit — becomes [LoginUiState.generalError], never attributed to the email or password
 * field alone (a wrong email and a wrong password are indistinguishable by design, so the UI must
 * not leak a distinction the server itself never makes). Pure function — no `MentoraSdk`, no
 * Android runtime — unit-tested directly (mirrors `AuthGateDecisionTest`'s approach for
 * [com.mentora.android.navigation.decideAuthGate]).
 */
fun mapLoginFailure(failure: ApiResult.Failure): ApiErrorCode = failure.code

/**
 * Register's error-routing rule (`ux/SCREEN_UX_SPECS.md § 7`, D51): `fields["email"]`/
 * `fields["password"]` route to their own field's error slot; [ApiErrorCode.EmailAlreadyRegistered]
 * routes under Email even though it is never present in `fields` (the D51 precedent); everything
 * else becomes [RegisterUiState.generalError]. Pure function — no `MentoraSdk`, no Android runtime.
 */
fun mapRegisterFailure(failure: ApiResult.Failure): Triple<EmailFieldError?, PasswordFieldError?, ApiErrorCode?> {
    val emailError = when {
        failure.fields?.get("email") == "INVALID" -> EmailFieldError.Invalid
        failure.code == ApiErrorCode.EmailAlreadyRegistered -> EmailFieldError.AlreadyRegistered
        else -> null
    }
    val passwordError = if (failure.fields?.get("password") == "WEAK") PasswordFieldError.Weak else null
    val generalError = if (emailError == null && passwordError == null) failure.code else null
    return Triple(emailError, passwordError, generalError)
}

/**
 * T7 — one screen-agnostic ViewModel backing both [com.mentora.android.ui.auth.LoginScreen] and
 * [com.mentora.android.ui.auth.RegisterScreen] (separate [LoginUiState]/[RegisterUiState] holders,
 * per the task's own "your call, whichever is cleaner" allowance — the two screens' field sets
 * differ enough, Register also has `name`, that two data classes are clearer than one shared shape).
 *
 * Calls only `sdk.auth.login`/`sdk.auth.register` ([com.mentora.shared.domain.usecase.auth.LoginUseCase]/
 * [com.mentora.shared.domain.usecase.auth.RegisterUseCase]) — never navigates itself on success.
 * `MentoraNavHost`'s own `LaunchedEffect(authState)` is the one place that reacts to the resulting
 * `AuthState.Authenticated` transition (`sdk.auth`'s `SessionManager` updates that state as a side
 * effect of a successful login/register) and performs the actual navigation.
 */
class AuthViewModel(private val sdk: MentoraSdk) : ViewModel() {

    private val _loginState = MutableStateFlow(LoginUiState())
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    private val _registerState = MutableStateFlow(RegisterUiState())
    val registerState: StateFlow<RegisterUiState> = _registerState.asStateFlow()

    fun onLoginEmailChange(value: String) {
        _loginState.update { it.copy(email = value, generalError = null) }
    }

    fun onLoginPasswordChange(value: String) {
        _loginState.update { it.copy(password = value, generalError = null) }
    }

    fun login() {
        val current = _loginState.value
        if (current.isLoading || current.email.isBlank() || current.password.isBlank()) return
        _loginState.update { it.copy(isLoading = true, generalError = null) }
        viewModelScope.launch {
            when (val result = sdk.auth.login(current.email, current.password)) {
                is ApiResult.Success -> _loginState.update { it.copy(isLoading = false, generalError = null) }
                is ApiResult.Failure -> _loginState.update {
                    it.copy(isLoading = false, generalError = mapLoginFailure(result))
                }
            }
        }
    }

    fun onRegisterNameChange(value: String) {
        _registerState.update { it.copy(name = value, generalError = null) }
    }

    fun onRegisterEmailChange(value: String) {
        _registerState.update { it.copy(email = value, generalError = null, emailError = null) }
    }

    fun onRegisterPasswordChange(value: String) {
        _registerState.update { it.copy(password = value, generalError = null, passwordError = null) }
    }

    fun register() {
        val current = _registerState.value
        if (current.isLoading || current.name.isBlank() || current.email.isBlank() || current.password.isBlank()) {
            return
        }
        _registerState.update { it.copy(isLoading = true, generalError = null, emailError = null, passwordError = null) }
        viewModelScope.launch {
            when (val result = sdk.auth.register(current.email, current.password, current.name)) {
                is ApiResult.Success -> _registerState.update {
                    it.copy(isLoading = false, generalError = null, emailError = null, passwordError = null)
                }
                is ApiResult.Failure -> {
                    val (emailError, passwordError, generalError) = mapRegisterFailure(result)
                    _registerState.update {
                        it.copy(isLoading = false, emailError = emailError, passwordError = passwordError, generalError = generalError)
                    }
                }
            }
        }
    }

    /** Plain [ViewModelProvider.Factory] — mirrors `AppSessionViewModel.Factory`'s exact idiom
     * (no DI framework layered on top of `shared`'s own Koin graph in this app). */
    class Factory(private val sdk: MentoraSdk) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AuthViewModel(sdk) as T
    }
}
