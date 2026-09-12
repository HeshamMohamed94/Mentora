package com.mentora.shared.data.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Serializable
private data class TestPayload(val id: String, val name: String)

class EnvelopeTest {

    @Test
    fun `success envelope round-trips with nextCursor present`() {
        val original = ApiSuccess(
            data = TestPayload(id = "abc123", name = "Course One"),
            meta = ApiMeta(requestId = "req-1", nextCursor = "cursor-xyz"),
        )
        val serializer = ApiSuccess.serializer(TestPayload.serializer())

        val json = MentoraJson.encodeToString(serializer, original)
        val decoded = MentoraJson.decodeFromString(serializer, json)

        assertEquals(original, decoded)
        assertEquals("cursor-xyz", decoded.meta.nextCursor)
    }

    @Test
    fun `success envelope with omitted nextCursor deserializes to null`() {
        val json = """{"data":{"id":"abc123","name":"Course One"},"meta":{"requestId":"req-1"}}"""
        val serializer = ApiSuccess.serializer(TestPayload.serializer())

        val decoded = MentoraJson.decodeFromString(serializer, json)

        assertNull(decoded.meta.nextCursor)
        assertEquals("req-1", decoded.meta.requestId)
        assertEquals(TestPayload("abc123", "Course One"), decoded.data)
    }

    @Test
    fun `success envelope round-trips with a list payload`() {
        val original = ApiSuccess(
            data = listOf(TestPayload("1", "One"), TestPayload("2", "Two")),
            meta = ApiMeta(requestId = "req-2"),
        )
        val serializer = ApiSuccess.serializer(ListSerializer(TestPayload.serializer()))

        val json = MentoraJson.encodeToString(serializer, original)
        val decoded = MentoraJson.decodeFromString(serializer, json)

        assertEquals(original, decoded)
    }

    @Test
    fun `error envelope round-trips with fields present`() {
        val original = ApiError(
            error = ApiErrorBody(
                code = "VALIDATION_ERROR",
                message = "The request could not be validated.",
                fields = mapOf("email" to "INVALID"),
            ),
            meta = ApiMeta(requestId = "req-3"),
        )

        val json = MentoraJson.encodeToString(ApiError.serializer(), original)
        val decoded = MentoraJson.decodeFromString(ApiError.serializer(), json)

        assertEquals(original, decoded)
    }

    @Test
    fun `error envelope with omitted fields deserializes to null`() {
        val json = """{"error":{"code":"COURSE_NOT_FOUND","message":"The course was not found."},"meta":{"requestId":"req-4"}}"""

        val decoded = MentoraJson.decodeFromString(ApiError.serializer(), json)

        assertNull(decoded.error.fields)
        assertEquals("COURSE_NOT_FOUND", decoded.error.code)
    }

    @Test
    fun `error envelope maps to a typed Failure via toFailure`() {
        val envelope = ApiError(
            error = ApiErrorBody(code = "FORBIDDEN_NOT_ENROLLED", message = "You are not enrolled in this course."),
            meta = ApiMeta(requestId = "req-5"),
        )

        val failure = envelope.toFailure(httpStatus = 403)

        assertEquals(ApiErrorCode.ForbiddenNotEnrolled, failure.code)
        assertEquals(403, failure.httpStatus)
        assertNull(failure.fields)
    }

    @Test
    fun `unknown error code falls back to ApiErrorCode Unknown instead of throwing`() {
        val json = """{"error":{"code":"SOME_FUTURE_CODE","message":"n/a"},"meta":{"requestId":"req-6"}}"""

        val decoded = MentoraJson.decodeFromString(ApiError.serializer(), json)
        val failure = decoded.toFailure(httpStatus = 400)

        assertEquals(ApiErrorCode.Unknown("SOME_FUTURE_CODE"), failure.code)
    }
}
