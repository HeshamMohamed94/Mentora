package com.mentora.shared.domain.validation

/**
 * A single field-level validation failure. [field] is the wire/DTO field name (e.g. `"email"`,
 * matching the backend's own `ApiException.Validation(fields = mapOf(...))` keys); [reason] is a
 * machine-checkable code (e.g. `"WEAK"`, `"INVALID"`) — never a localized message. UI-facing copy
 * is always derived from [reason] by the platform layer, never shown verbatim.
 */
data class FieldError(val field: String, val reason: String)

/**
 * The result shape every validator in this package returns — never a thrown exception. A UX
 * nicety only: it mirrors the backend's own authoritative validation as closely as possible, but
 * never replaces it — every network call still goes to the server and the server's response
 * remains the actual source of truth (see `EmailValidator`/`PasswordValidator` kdoc).
 */
sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Invalid(val errors: List<FieldError>) : ValidationResult()
}
