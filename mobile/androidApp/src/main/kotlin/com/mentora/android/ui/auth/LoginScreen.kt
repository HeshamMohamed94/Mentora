package com.mentora.android.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mentora.android.R
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.ui.components.MentoraTextField
import com.mentora.android.ui.components.MentoraTextButton
import com.mentora.android.ui.components.PasswordField
import com.mentora.android.ui.components.PrimaryButton
import com.mentora.android.ui.error.apiErrorMessage
import com.mentora.shared.MentoraSdk

/**
 * T7 — the real Login screen (`ux/SCREEN_UX_SPECS.md § 6`, `design-to-code/screens/login.json`,
 * an `approved-pattern` derived from `design-system/COMPONENTS.md`'s locked TextField/PasswordField/
 * Button specs — no screen-specific showcase mockup exists for Login, per that json's own `d51Note`).
 *
 * Deliberately calls ONLY `sdk.auth.login(...)` and renders loading/error state — it never navigates
 * on its own success. `MentoraNavHost`'s own `LaunchedEffect(authState)` is the one place that reacts
 * to the resulting `AuthState.Authenticated` transition and performs the actual navigation (pending-
 * intent-return or Home) — see that composable's kdoc. No "Forgot password?" link/flow exists here
 * (`product/MVP_SCOPE.md` explicitly excludes it; there is no backend endpoint for it).
 */
@Composable
fun LoginScreen(
    sdk: MentoraSdk,
    onOpenRegister: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory(sdk))
    val uiState by viewModel.loginState.collectAsState()

    LoginScreenContent(
        uiState = uiState,
        onEmailChange = viewModel::onLoginEmailChange,
        onPasswordChange = viewModel::onLoginPasswordChange,
        onSubmit = viewModel::login,
        onOpenRegister = onOpenRegister,
        modifier = modifier,
    )
}

/**
 * The stateless presentation half of [LoginScreen] — split out so instrumented tests can drive every
 * rendering/interaction state (empty-field-disabled, the general error banner, the Register link)
 * with a hand-built [LoginUiState], with no [MentoraSdk]/[AuthViewModel] involved at all (mirrors why
 * `MentoraNavHost` itself takes a plain `authState: AuthState` parameter rather than reading `sdk.auth`
 * directly — see `AuthGate.kt`'s kdoc for the same seam rationale).
 */
@Composable
internal fun LoginScreenContent(
    uiState: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onOpenRegister: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSubmit = uiState.email.isNotBlank() && uiState.password.isNotBlank() && !uiState.isLoading

    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(MentoraDimens.spacing.space4),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = AuthFormMaxWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Minimal logo/wordmark-only header (`ux/SCREEN_UX_SPECS.md § 6`: "no Navbar link row —
            // nothing to navigate to from a focused auth form"). MentoraNavHost's generic MentoraTopBar
            // chrome is hidden for this destination (see that composable's kdoc) so this IS the header.
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = MentoraDimens.spacing.space6),
            )

            AuthCard {
                Text(
                    text = stringResource(R.string.auth_login_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = MentoraDimens.spacing.space6),
                )

                MentoraTextField(
                    value = uiState.email,
                    onValueChange = onEmailChange,
                    label = stringResource(R.string.auth_email_label),
                    modifier = Modifier.fillMaxWidth().testTag(LoginEmailFieldTestTag),
                    keyboardType = KeyboardType.Email,
                    enabled = !uiState.isLoading,
                )

                Column(modifier = Modifier.padding(top = MentoraDimens.spacing.space4)) {
                    PasswordField(
                        value = uiState.password,
                        onValueChange = onPasswordChange,
                        label = stringResource(R.string.auth_password_label),
                        showPasswordLabel = stringResource(R.string.auth_show_password),
                        hidePasswordLabel = stringResource(R.string.auth_hide_password),
                        modifier = Modifier.fillMaxWidth().testTag(LoginPasswordFieldTestTag),
                        enabled = !uiState.isLoading,
                    )
                }

                // Inline, NON-field-specific banner (`ux/SCREEN_UX_SPECS.md § 6`'s "for security" —
                // AUTH_INVALID_CREDENTIALS, and every other Login failure, is never attributed to
                // either field alone).
                uiState.generalError?.let { code ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = MentoraDimens.spacing.space4)
                            .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small)
                            .padding(horizontal = MentoraDimens.spacing.space4, vertical = MentoraDimens.spacing.space3)
                            .testTag(LoginGeneralErrorTestTag),
                    ) {
                        Text(
                            text = apiErrorMessage(code),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }

                PrimaryButton(
                    text = stringResource(R.string.auth_login_button),
                    onClick = onSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MentoraDimens.spacing.space6)
                        .testTag(LoginSubmitButtonTestTag),
                    enabled = canSubmit,
                    loading = uiState.isLoading,
                )

                MentoraTextButton(
                    text = stringResource(R.string.auth_login_secondary_link),
                    onClick = onOpenRegister,
                    modifier = Modifier
                        .padding(top = MentoraDimens.spacing.space4)
                        .testTag(LoginOpenRegisterTestTag),
                )
            }
        }
    }
}

/** `web/src/app/components.css`'s `.mtx-auth-card`: 480px max-width, `border.default`, `radius.large`
 * (`MaterialTheme.shapes.medium`), `surface.default` background, `elevation.1` (low tonal elevation
 * over a heavy shadow, per `PHASE_4_ANDROID_PLAN.md § 3`), `space.6` padding. Shared by
 * [LoginScreenContent] and [com.mentora.android.ui.auth.RegisterScreenContent]. */
@Composable
internal fun AuthCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .border(MentoraDimens.borderWidthDefault, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(MentoraDimens.spacing.space6),
        content = content,
    )
}

/** `web/src/app/components.css`'s `.mtx-auth-card { max-width: 480px }` — no design-tokens.json
 * spacing-scale step matches 480 (disclosed the same way `MentoraButton.kt`'s 88dp min-width is). */
internal val AuthFormMaxWidth = 480.dp

// Test-only hooks (`ui.test.onNodeWithTag`), unused by production code otherwise.
const val LoginEmailFieldTestTag = "login-email-field"
const val LoginPasswordFieldTestTag = "login-password-field"
const val LoginGeneralErrorTestTag = "login-general-error"
const val LoginSubmitButtonTestTag = "login-submit-button"
const val LoginOpenRegisterTestTag = "login-open-register"
