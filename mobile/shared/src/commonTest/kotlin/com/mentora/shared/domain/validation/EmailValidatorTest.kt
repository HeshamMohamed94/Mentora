package com.mentora.shared.domain.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmailValidatorTest {

    @Test
    fun `valid emails pass, normalized to trimmed lowercase`() {
        val cases = listOf(
            "  Student@Example.com  " to "student@example.com",
            "a.b+tag@sub.example.co" to "a.b+tag@sub.example.co",
        )
        for ((raw, expectedNormalized) in cases) {
            val (normalized, result) = EmailValidator.validate(raw)
            assertEquals(expectedNormalized, normalized, "raw=$raw")
            assertEquals(ValidationResult.Valid, result, "raw=$raw")
        }
    }

    @Test
    fun `invalid emails fail with an INVALID field error`() {
        val invalidEmails = listOf("", "not-an-email", "missing-domain@", "@missing-local.com", "a b@example.com")
        for (raw in invalidEmails) {
            val (_, result) = EmailValidator.validate(raw)
            require(result is ValidationResult.Invalid) { "expected $raw to be invalid" }
            assertEquals(listOf(FieldError("email", "INVALID")), result.errors)
        }
    }

    @Test
    fun `an email longer than 254 characters is invalid`() {
        val longLocalPart = "a".repeat(250)
        val (_, result) = EmailValidator.validate("$longLocalPart@example.com")

        assertTrue(result is ValidationResult.Invalid)
    }
}
