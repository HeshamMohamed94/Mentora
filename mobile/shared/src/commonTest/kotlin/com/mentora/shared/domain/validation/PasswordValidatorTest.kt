package com.mentora.shared.domain.validation

import kotlin.test.Test
import kotlin.test.assertEquals

class PasswordValidatorTest {

    @Test
    fun `at least 8 chars with a letter and a digit is valid`() {
        val validPasswords = listOf("password1", "12345678a", "Ab1defgh", "a1234567")
        for (password in validPasswords) {
            assertEquals(ValidationResult.Valid, PasswordValidator.validate(password), "password=$password")
        }
    }

    @Test
    fun `shorter than 8 characters is WEAK`() {
        assertEquals(
            ValidationResult.Invalid(listOf(FieldError("password", "WEAK"))),
            PasswordValidator.validate("ab1"),
        )
    }

    @Test
    fun `no digit is WEAK`() {
        assertEquals(
            ValidationResult.Invalid(listOf(FieldError("password", "WEAK"))),
            PasswordValidator.validate("passwordonly"),
        )
    }

    @Test
    fun `no letter is WEAK`() {
        assertEquals(
            ValidationResult.Invalid(listOf(FieldError("password", "WEAK"))),
            PasswordValidator.validate("12345678"),
        )
    }
}
