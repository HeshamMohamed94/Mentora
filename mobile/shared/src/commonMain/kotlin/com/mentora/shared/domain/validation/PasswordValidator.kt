package com.mentora.shared.domain.validation

/**
 * Mirrors `backend/src/main/kotlin/com/mentora/backend/auth/service/AuthService.kt`'s
 * `validatePassword` exactly:
 * ```
 * if (password.length < 8 || password.none(Char::isLetter) || password.none(Char::isDigit)) {
 *     throw ApiException.Validation(fields = mapOf("password" to "WEAK"))
 * }
 * ```
 * i.e. at least 8 characters, at least one letter, at least one digit — no other composition rule
 * (no required uppercase/symbol). A UX nicety only — never replaces the server's own authoritative
 * check (see [EmailValidator] kdoc for the same caveat).
 */
object PasswordValidator {
    private const val MIN_LENGTH = 8

    fun validate(password: String): ValidationResult {
        val weak = password.length < MIN_LENGTH || password.none(Char::isLetter) || password.none(Char::isDigit)
        return if (weak) {
            ValidationResult.Invalid(listOf(FieldError("password", "WEAK")))
        } else {
            ValidationResult.Valid
        }
    }
}
