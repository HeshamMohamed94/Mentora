package com.mentora.shared.domain.validation

/**
 * Mirrors `backend/src/main/kotlin/com/mentora/backend/auth/service/AuthService.kt`'s
 * `normalizeAndValidateEmail`/`EMAIL` regex exactly:
 * ```
 * val email = raw.trim().lowercase()
 * if (email.length > 254 || !EMAIL.matches(email)) throw ApiException.Validation(fields = mapOf("email" to "INVALID"))
 * ```
 * This is a UX nicety only — it fails fast, with zero network round trip, for an obviously
 * malformed email. It never replaces the server's own authoritative check: every register/login
 * call still goes to the network, and the server can still reject an email this validator
 * accepted (e.g. `EMAIL_ALREADY_REGISTERED`), or a future backend regex change could make the two
 * diverge until this mirror is updated to match.
 */
object EmailValidator {
    private val EMAIL_REGEX = Regex(
        "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?" +
            "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$",
    )
    private const val MAX_LENGTH = 254

    /**
     * Returns the normalized (trimmed + lowercased) email alongside the validation result — every
     * caller needs the normalized form to actually send over the wire regardless of outcome,
     * exactly as the backend normalizes before validating.
     */
    fun validate(raw: String): Pair<String, ValidationResult> {
        val normalized = raw.trim().lowercase()
        val valid = normalized.length <= MAX_LENGTH && EMAIL_REGEX.matches(normalized)
        val result = if (valid) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(listOf(FieldError("email", "INVALID")))
        }
        return normalized to result
    }
}
