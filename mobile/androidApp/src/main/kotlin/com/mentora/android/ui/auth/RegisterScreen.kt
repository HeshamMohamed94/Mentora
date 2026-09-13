package com.mentora.android.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
 * T7 — the real Register screen (`ux/SCREEN_UX_SPECS.md § 7`, `design-to-code/screens/register.json`
 * — same `approved-pattern` classification/rationale as [LoginScreen]).
 *
 * Deliberately calls ONLY `sdk.auth.register(...)` and renders loading/error state — never navigates
 * on its own success (see [LoginScreen]'s kdoc for the full rationale, identical here).
 */
@Composable
fun RegisterScreen(
    sdk: MentoraSdk,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory(sdk))
    val uiState by viewModel.registerState.collectAsState()

    RegisterScreenContent(
        uiState = uiState,
        onNameChange = viewModel::onRegisterNameChange,
        onEmailChange = viewModel::onRegisterEmailChange,
        onPasswordChange = viewModel::onRegisterPasswordChange,
        onSubmit = viewModel::register,
        onOpenLogin = onOpenLogin,
        modifier = modifier,
    )
}

/** The stateless presentation half of [RegisterScreen] — see [LoginScreenContent]'s kdoc for why this
 * split exists (instrumented-testable with a hand-built [RegisterUiState], no [MentoraSdk] involved). */
@Composable
internal fun RegisterScreenContent(
    uiState: RegisterUiState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSubmit = uiState.name.isNotBlank() && uiState.email.isNotBlank() &&
        uiState.password.isNotBlank() && !uiState.isLoading

    val emailErrorText = when (uiState.emailError) {
        EmailFieldError.Invalid -> stringResource(R.string.auth_email_invalid)
        EmailFieldError.AlreadyRegistered -> stringResource(R.string.error_email_already_registered)
        null -> null
    }
    val passwordErrorText = when (uiState.passwordError) {
        PasswordFieldError.Weak -> stringResource(R.string.auth_password_weak)
        null -> null
    }

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
            // Minimal logo/wordmark-only header — same treatment as Login (`ux/SCREEN_UX_SPECS.md § 7`:
            // "same minimal treatment as Login"). MentoraTopBar's generic chrome is hidden for this
            // destination too — see MentoraNavHost's kdoc.
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = MentoraDimens.spacing.space6),
            )

            AuthCard {
                Text(
                    text = stringResource(R.string.auth_register_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = MentoraDimens.spacing.space6),
                )

                MentoraTextField(
                    value = uiState.name,
                    onValueChange = onNameChange,
                    label = stringResource(R.string.auth_name_label),
                    modifier = Modifier.fillMaxWidth().testTag(RegisterNameFieldTestTag),
                    enabled = !uiState.isLoading,
                )

                MentoraTextField(
                    value = uiState.email,
                    onValueChange = onEmailChange,
                    label = stringResource(R.string.auth_email_label),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MentoraDimens.spacing.space4)
                        .testTag(RegisterEmailFieldTestTag),
                    keyboardType = KeyboardType.Email,
                    enabled = !uiState.isLoading,
                    // D51 precedent: EMAIL_ALREADY_REGISTERED/INVALID both render directly under the
                    // Email field, never as a generic top-of-form banner (ux/SCREEN_UX_SPECS.md § 7).
                    errorText = emailErrorText,
                )

                PasswordField(
                    value = uiState.password,
                    onValueChange = onPasswordChange,
                    label = stringResource(R.string.auth_password_label),
                    showPasswordLabel = stringResource(R.string.auth_show_password),
                    hidePasswordLabel = stringResource(R.string.auth_hide_password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MentoraDimens.spacing.space4)
                        .testTag(RegisterPasswordFieldTestTag),
                    enabled = !uiState.isLoading,
                    errorText = passwordErrorText,
                    // The strength hint is a caption HELPER text, visible before submit — not a
                    // blocking rule beyond the actual PasswordValidator check
                    // (`ux/SCREEN_UX_SPECS.md § 7`: "not a blocking rule beyond basic validation").
                    // MentoraFieldSupportingText only ever shows one of helper/error — error wins once
                    // a WEAK failure actually arrives, exactly per that composable's own contract.
                    helperText = if (passwordErrorText == null) stringResource(R.string.auth_password_hint) else null,
                )

                // Everything that ISN'T email/password-field-specific (any other server failure) —
                // a general banner, same treatment as Login's.
                uiState.generalError?.let { code ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = MentoraDimens.spacing.space4)
                            .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small)
                            .padding(horizontal = MentoraDimens.spacing.space4, vertical = MentoraDimens.spacing.space3)
                            .testTag(RegisterGeneralErrorTestTag),
                    ) {
                        Text(
                            text = apiErrorMessage(code),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }

                PrimaryButton(
                    text = stringResource(R.string.auth_register_button),
                    onClick = onSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MentoraDimens.spacing.space6)
                        .testTag(RegisterSubmitButtonTestTag),
                    enabled = canSubmit,
                    loading = uiState.isLoading,
                )

                MentoraTextButton(
                    text = stringResource(R.string.auth_register_secondary_link),
                    onClick = onOpenLogin,
                    modifier = Modifier
                        .padding(top = MentoraDimens.spacing.space4)
                        .testTag(RegisterOpenLoginTestTag),
                )
            }
        }
    }
}

// Test-only hooks (`ui.test.onNodeWithTag`), unused by production code otherwise.
const val RegisterNameFieldTestTag = "register-name-field"
const val RegisterEmailFieldTestTag = "register-email-field"
const val RegisterPasswordFieldTestTag = "register-password-field"
const val RegisterGeneralErrorTestTag = "register-general-error"
const val RegisterSubmitButtonTestTag = "register-submit-button"
const val RegisterOpenLoginTestTag = "register-open-login"
