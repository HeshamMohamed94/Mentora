package com.mentora.shared.data.network

/**
 * Synthesizes an [ApiResult.Failure] for a `429 Too Many Requests` response, per Decision C2 in
 * `execution/PHASE_3_KMP_PLAN.md`: the backend's `RateLimit` Ktor plugin (`plugins/RateLimiting.kt`)
 * responds `429` with **no JSON envelope and no error code** — there is no `StatusPages` handler
 * for it, unlike every other `ApiException`-driven failure. A real 429 body is therefore expected
 * to be empty/unparseable; [rawBody] is still given the chance to parse as the normal error
 * envelope first, purely as defensive forward-compatibility in case that ever changes.
 *
 * Pure function (no networking) so Task 3's `ApiClient` can call it after receiving a raw 429
 * response, and so it is directly unit-testable here without an HTTP stack.
 */
fun synthesizeRateLimitedFailure(
    httpStatus: Int,
    requestPath: String,
    rawBody: String?,
): ApiResult.Failure {
    parseErrorEnvelopeOrNull(rawBody)?.let { envelope -> return envelope.toFailure(httpStatus) }

    // Real backend routes are mounted under "/api/v1/auth/..." and "/api/v1/ai-tutor/..."
    // (see AuthRoutes.kt/AiTutorRoutes.kt) — matched by substring, not `startsWith`, so this
    // is correct regardless of whether the caller passes the full path (with the "/api/v1"
    // prefix) or a base-URL-relative path (without it).
    val code = when {
        requestPath.contains("/auth/") -> ApiErrorCode.RateLimitedAuth
        requestPath.contains("/ai-tutor/") -> ApiErrorCode.RateLimitedAiTutor
        else -> ApiErrorCode.Unknown("RATE_LIMITED")
    }
    return ApiResult.Failure(
        code = code,
        message = "Too many requests. Please slow down.",
        fields = null,
        httpStatus = httpStatus,
    )
}

private fun parseErrorEnvelopeOrNull(rawBody: String?): ApiError? {
    if (rawBody.isNullOrBlank()) return null
    return try {
        MentoraJson.decodeFromString(ApiError.serializer(), rawBody)
    } catch (e: Exception) {
        null
    }
}
