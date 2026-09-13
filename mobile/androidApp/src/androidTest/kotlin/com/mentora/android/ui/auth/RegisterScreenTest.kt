package com.mentora.android.ui.auth

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mentora.android.theme.MentoraTheme
import com.mentora.android.ui.components.MentoraButtonSpinnerTestTag
import com.mentora.android.ui.components.MentoraFieldErrorIconTestTag
import com.mentora.shared.data.network.ApiErrorCode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T7 — real rendering/interaction coverage for [RegisterScreenContent] (the stateless presentation
 * half of [RegisterScreen] — no [com.mentora.shared.MentoraSdk] needed, every state is hand-built,
 * mirroring [LoginScreenTest]).
 */
@RunWith(AndroidJUnit4::class)
class RegisterScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun submitIsDisabledUntilAllThreeFieldsAreNonEmpty() {
        composeTestRule.setContent {
            MentoraTheme {
                RegisterScreenContent(
                    uiState = RegisterUiState(name = "Ada", email = "ada@example.com"), // password still blank
                    onNameChange = {},
                    onEmailChange = {},
                    onPasswordChange = {},
                    onSubmit = {},
                    onOpenLogin = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(RegisterSubmitButtonTestTag).assertIsNotEnabled()
    }

    @Test
    fun submitBecomesEnabledOnceAllThreeFieldsAreFilled() {
        composeTestRule.setContent {
            MentoraTheme {
                RegisterScreenContent(
                    uiState = RegisterUiState(name = "Ada", email = "ada@example.com", password = "password1"),
                    onNameChange = {},
                    onEmailChange = {},
                    onPasswordChange = {},
                    onSubmit = {},
                    onOpenLogin = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(RegisterSubmitButtonTestTag).assertIsEnabled()
    }

    /** `fields["email"] == "INVALID"` (a [ApiErrorCode.ValidationError] failure) must place its
     * message directly under the Email field, never as a top-of-form general banner (D51 precedent,
     * `ux/SCREEN_UX_SPECS.md § 7`). */
    @Test
    fun validationErrorPlacesTheMessageUnderTheEmailField_notAGeneralBanner() {
        composeTestRule.setContent {
            MentoraTheme {
                RegisterScreenContent(
                    uiState = RegisterUiState(
                        name = "Ada",
                        email = "not-an-email",
                        password = "password1",
                        emailError = EmailFieldError.Invalid,
                    ),
                    onNameChange = {},
                    onEmailChange = {},
                    onPasswordChange = {},
                    onSubmit = {},
                    onOpenLogin = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Enter a valid email address.").assertExists()
        composeTestRule.onNodeWithTag(MentoraFieldErrorIconTestTag, useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag(RegisterGeneralErrorTestTag).assertDoesNotExist()
    }

    /** [ApiErrorCode.EmailAlreadyRegistered] is never present in `fields` but still routes under the
     * Email field per the D51 precedent (`web/.../register-form.tsx`'s `setError("email", ...)`). */
    @Test
    fun emailAlreadyRegisteredAlsoPlacesUnderTheEmailField_notAGeneralBanner() {
        composeTestRule.setContent {
            MentoraTheme {
                RegisterScreenContent(
                    uiState = RegisterUiState(
                        name = "Ada",
                        email = "ada@example.com",
                        password = "password1",
                        emailError = EmailFieldError.AlreadyRegistered,
                    ),
                    onNameChange = {},
                    onEmailChange = {},
                    onPasswordChange = {},
                    onSubmit = {},
                    onOpenLogin = {},
                )
            }
        }

        composeTestRule.onNodeWithText("An account with this email already exists.").assertExists()
        composeTestRule.onNodeWithTag(RegisterGeneralErrorTestTag).assertDoesNotExist()
    }

    @Test
    fun weakPasswordPlacesTheMessageUnderThePasswordField() {
        composeTestRule.setContent {
            MentoraTheme {
                RegisterScreenContent(
                    uiState = RegisterUiState(
                        name = "Ada",
                        email = "ada@example.com",
                        password = "short",
                        passwordError = PasswordFieldError.Weak,
                    ),
                    onNameChange = {},
                    onEmailChange = {},
                    onPasswordChange = {},
                    onSubmit = {},
                    onOpenLogin = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Password must be at least 8 characters, with a letter and a number.").assertExists()
        composeTestRule.onNodeWithTag(RegisterGeneralErrorTestTag).assertDoesNotExist()
    }

    /** An unrelated failure (not email/password-specific) falls through to the general banner. */
    @Test
    fun anUnrelatedFailureRendersAsAGeneralBanner() {
        composeTestRule.setContent {
            MentoraTheme {
                RegisterScreenContent(
                    uiState = RegisterUiState(
                        name = "Ada",
                        email = "ada@example.com",
                        password = "password1",
                        generalError = ApiErrorCode.InternalError,
                    ),
                    onNameChange = {},
                    onEmailChange = {},
                    onPasswordChange = {},
                    onSubmit = {},
                    onOpenLogin = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(RegisterGeneralErrorTestTag).assertExists()
        composeTestRule.onNodeWithText("Something went wrong. Please try again.").assertExists()
    }

    @Test
    fun successClearsLoading_andNeverInvokesNavigationItself() {
        var openLoginCount = 0
        composeTestRule.setContent {
            MentoraTheme {
                RegisterScreenContent(
                    uiState = RegisterUiState(
                        name = "Ada",
                        email = "ada@example.com",
                        password = "password1",
                        isLoading = false,
                    ),
                    onNameChange = {},
                    onEmailChange = {},
                    onPasswordChange = {},
                    onSubmit = {},
                    onOpenLogin = { openLoginCount++ },
                )
            }
        }

        composeTestRule.onNodeWithTag(MentoraButtonSpinnerTestTag).assertDoesNotExist()
        composeTestRule.onNodeWithTag(RegisterGeneralErrorTestTag).assertDoesNotExist()
        assertEquals(0, openLoginCount)
    }

    @Test
    fun theLoginLinkNavigatesToLogin() {
        var openLoginCount = 0
        composeTestRule.setContent {
            MentoraTheme {
                RegisterScreenContent(
                    uiState = RegisterUiState(),
                    onNameChange = {},
                    onEmailChange = {},
                    onPasswordChange = {},
                    onSubmit = {},
                    onOpenLogin = { openLoginCount++ },
                )
            }
        }

        composeTestRule.onNodeWithTag(RegisterOpenLoginTestTag).performClick()

        assertEquals(1, openLoginCount)
    }
}
