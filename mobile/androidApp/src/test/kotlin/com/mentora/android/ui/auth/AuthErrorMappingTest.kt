package com.mentora.android.ui.auth

import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * T7 — plain JVM unit test (`testDebugUnitTest`) for [mapLoginFailure]/[mapRegisterFailure], the pure
 * error-routing functions backing [AuthViewModel]. Mirrors `AuthGateDecisionTest`'s approach for
 * [com.mentora.android.navigation.decideAuthGate]: no Android runtime, no `MentoraSdk` at all — the
 * one genuinely awkward-to-drive part ([AuthViewModel] itself, which needs a real `MentoraSdk` to
 * actually call `sdk.auth.login`/`register`) is deliberately NOT what this test covers; only the pure
 * mapping logic is.
 */
class AuthErrorMappingTest {

    // ---- mapLoginFailure: EVERY failure becomes a general error, never field-specific ----

    @Test
    fun `AUTH_INVALID_CREDENTIALS maps straight through as a general error`() {
        val failure = ApiResult.Failure(
            code = ApiErrorCode.AuthInvalidCredentials,
            message = "diagnostic only",
            fields = null,
            httpStatus = 401,
        )

        assertEquals(ApiErrorCode.AuthInvalidCredentials, mapLoginFailure(failure))
    }

    @Test
    fun `a rate-limited login failure also maps straight through as a general error`() {
        val failure = ApiResult.Failure(
            code = ApiErrorCode.RateLimitedAuth,
            message = "diagnostic only",
            fields = null,
            httpStatus = 429,
        )

        assertEquals(ApiErrorCode.RateLimitedAuth, mapLoginFailure(failure))
    }

    // ---- mapRegisterFailure: field-specific routing ----

    @Test
    fun `fields email=INVALID routes to the email field, no general error`() {
        val failure = ApiResult.Failure(
            code = ApiErrorCode.ValidationError,
            message = "diagnostic only",
            fields = mapOf("email" to "INVALID"),
            httpStatus = 0,
        )

        val (emailError, passwordError, generalError) = mapRegisterFailure(failure)

        assertEquals(EmailFieldError.Invalid, emailError)
        assertNull(passwordError)
        assertNull(generalError)
    }

    @Test
    fun `fields password=WEAK routes to the password field, no general error`() {
        val failure = ApiResult.Failure(
            code = ApiErrorCode.ValidationError,
            message = "diagnostic only",
            fields = mapOf("password" to "WEAK"),
            httpStatus = 0,
        )

        val (emailError, passwordError, generalError) = mapRegisterFailure(failure)

        assertNull(emailError)
        assertEquals(PasswordFieldError.Weak, passwordError)
        assertNull(generalError)
    }

    @Test
    fun `both email and password field errors can route simultaneously`() {
        val failure = ApiResult.Failure(
            code = ApiErrorCode.ValidationError,
            message = "diagnostic only",
            fields = mapOf("email" to "INVALID", "password" to "WEAK"),
            httpStatus = 0,
        )

        val (emailError, passwordError, generalError) = mapRegisterFailure(failure)

        assertEquals(EmailFieldError.Invalid, emailError)
        assertEquals(PasswordFieldError.Weak, passwordError)
        assertNull(generalError)
    }

    @Test
    fun `EMAIL_ALREADY_REGISTERED routes under the email field even though it is never in fields`() {
        val failure = ApiResult.Failure(
            code = ApiErrorCode.EmailAlreadyRegistered,
            message = "diagnostic only",
            fields = null,
            httpStatus = 409,
        )

        val (emailError, passwordError, generalError) = mapRegisterFailure(failure)

        assertEquals(EmailFieldError.AlreadyRegistered, emailError)
        assertNull(passwordError)
        assertNull(generalError)
    }

    @Test
    fun `an unrelated failure code becomes a general error, no field is touched`() {
        val failure = ApiResult.Failure(
            code = ApiErrorCode.InternalError,
            message = "diagnostic only",
            fields = null,
            httpStatus = 500,
        )

        val (emailError, passwordError, generalError) = mapRegisterFailure(failure)

        assertNull(emailError)
        assertNull(passwordError)
        assertEquals(ApiErrorCode.InternalError, generalError)
    }
}
