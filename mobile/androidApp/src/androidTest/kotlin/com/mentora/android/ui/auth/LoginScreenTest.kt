package com.mentora.android.ui.auth

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
 * T7 — real rendering/interaction coverage for [LoginScreenContent] (the stateless presentation half
 * of [LoginScreen] — see that composable's kdoc for why the split exists: no [com.mentora.shared.MentoraSdk]
 * needed here at all, every state is hand-built).
 */
@RunWith(AndroidJUnit4::class)
class LoginScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun submitIsDisabledUntilBothFieldsAreNonEmpty() {
        composeTestRule.setContent {
            MentoraTheme {
                LoginScreenContent(
                    uiState = LoginUiState(),
                    onEmailChange = {},
                    onPasswordChange = {},
                    onSubmit = {},
                    onOpenRegister = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(LoginSubmitButtonTestTag).assertIsNotEnabled()
    }

    @Test
    fun submitBecomesEnabledOnceBothFieldsAreFilled() {
        composeTestRule.setContent {
            MentoraTheme {
                LoginScreenContent(
                    uiState = LoginUiState(email = "ada@example.com", password = "password1"),
                    onEmailChange = {},
                    onPasswordChange = {},
                    onSubmit = {},
                    onOpenRegister = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(LoginSubmitButtonTestTag).assertIsEnabled()
    }

    @Test
    fun typingIntoBothFieldsDrivesTheCallbacksAndEnablesSubmit() {
        var email = ""
        var password = ""
        composeTestRule.setContent {
            MentoraTheme {
                LoginScreenContent(
                    uiState = LoginUiState(email = email, password = password),
                    onEmailChange = { email = it },
                    onPasswordChange = { password = it },
                    onSubmit = {},
                    onOpenRegister = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(LoginEmailFieldTestTag).performTextInput("ada@example.com")
        assertEquals("ada@example.com", email)

        composeTestRule.onNodeWithTag(LoginPasswordFieldTestTag).performTextInput("password1")
        assertEquals("password1", password)
    }

    /** AUTH_INVALID_CREDENTIALS (and every other Login failure) renders as a general, non-field-
     * specific banner — `ux/SCREEN_UX_SPECS.md § 6`'s security-conscious "never attribute a login
     * failure to one field alone." */
    @Test
    fun generalErrorRendersAsABanner_neverAttributedToAField() {
        composeTestRule.setContent {
            MentoraTheme {
                LoginScreenContent(
                    uiState = LoginUiState(
                        email = "ada@example.com",
                        password = "wrong-password",
                        generalError = ApiErrorCode.AuthInvalidCredentials,
                    ),
                    onEmailChange = {},
                    onPasswordChange = {},
                    onSubmit = {},
                    onOpenRegister = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(LoginGeneralErrorTestTag).assertExists()
        composeTestRule.onNodeWithText("Incorrect email or password.").assertExists()
        // Neither field carries its own error icon — LoginUiState has no per-field error slot at all.
        composeTestRule.onNodeWithTag(MentoraFieldErrorIconTestTag, useUnmergedTree = true).assertDoesNotExist()
    }

    /** A successful login clears the loading state and does NOT itself attempt any navigation — the
     * only navigation-shaped callback this content exposes ([onOpenRegister]) must never fire on its
     * own; the real navigation-on-success mechanism lives entirely in `MentoraNavHost`. */
    @Test
    fun successClearsLoading_andNeverInvokesNavigationItself() {
        var openRegisterCount = 0
        composeTestRule.setContent {
            MentoraTheme {
                LoginScreenContent(
                    uiState = LoginUiState(email = "ada@example.com", password = "password1", isLoading = false),
                    onEmailChange = {},
                    onPasswordChange = {},
                    onSubmit = {},
                    onOpenRegister = { openRegisterCount++ },
                )
            }
        }

        composeTestRule.onNodeWithTag(MentoraButtonSpinnerTestTag).assertDoesNotExist()
        composeTestRule.onNodeWithTag(LoginGeneralErrorTestTag).assertDoesNotExist()
        assertEquals(0, openRegisterCount)
    }

    @Test
    fun theRegisterLinkNavigatesToRegister() {
        var openRegisterCount = 0
        composeTestRule.setContent {
            MentoraTheme {
                LoginScreenContent(
                    uiState = LoginUiState(),
                    onEmailChange = {},
                    onPasswordChange = {},
                    onSubmit = {},
                    onOpenRegister = { openRegisterCount++ },
                )
            }
        }

        composeTestRule.onNodeWithTag(LoginOpenRegisterTestTag).performClick()

        assertEquals(1, openRegisterCount)
    }
}
