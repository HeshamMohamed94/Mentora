package com.mentora.shared.data.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RateLimitTest {

    @Test
    fun `auth path synthesizes RATE_LIMITED_AUTH from an empty body`() {
        val failure = synthesizeRateLimitedFailure(
            httpStatus = 429,
            requestPath = "/auth/login",
            rawBody = "",
        )

        assertEquals(ApiErrorCode.RateLimitedAuth, failure.code)
        assertEquals(429, failure.httpStatus)
    }

    @Test
    fun `ai tutor path synthesizes RATE_LIMITED_AI_TUTOR from a null body`() {
        val failure = synthesizeRateLimitedFailure(
            httpStatus = 429,
            requestPath = "/ai-tutor/conversation/messages",
            rawBody = null,
        )

        assertEquals(ApiErrorCode.RateLimitedAiTutor, failure.code)
    }

    @Test
    fun `real backend path shape with the api v1 prefix still resolves correctly`() {
        val authFailure = synthesizeRateLimitedFailure(
            httpStatus = 429,
            requestPath = "/api/v1/auth/login",
            rawBody = null,
        )
        assertEquals(ApiErrorCode.RateLimitedAuth, authFailure.code)

        val aiTutorFailure = synthesizeRateLimitedFailure(
            httpStatus = 429,
            requestPath = "/api/v1/ai-tutor/conversation/messages",
            rawBody = null,
        )
        assertEquals(ApiErrorCode.RateLimitedAiTutor, aiTutorFailure.code)
    }

    @Test
    fun `other paths fall back to a generic rate-limited failure`() {
        val failure = synthesizeRateLimitedFailure(
            httpStatus = 429,
            requestPath = "/courses",
            rawBody = "not json at all",
        )

        assertEquals(ApiErrorCode.Unknown("RATE_LIMITED"), failure.code)
        assertNull(failure.fields)
    }

    @Test
    fun `a parseable error envelope body is preferred over synthesis`() {
        val body = """{"error":{"code":"FORBIDDEN_ROLE","message":"nope"},"meta":{"requestId":"req-1"}}"""

        val failure = synthesizeRateLimitedFailure(
            httpStatus = 429,
            requestPath = "/auth/login",
            rawBody = body,
        )

        assertEquals(ApiErrorCode.ForbiddenRole, failure.code)
    }
}
