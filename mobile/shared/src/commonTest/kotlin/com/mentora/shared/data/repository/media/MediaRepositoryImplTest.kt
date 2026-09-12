package com.mentora.shared.data.repository.media

import com.mentora.shared.config.ApiEnvironment
import com.mentora.shared.data.network.ApiClient
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.HttpClientFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

private val testEnvironment = ApiEnvironment.custom("http://localhost")

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

private fun repositoryFor(engine: MockEngine): MediaRepository =
    MediaRepositoryImpl(ApiClient(HttpClientFactory.create(engine, testEnvironment)))

class MediaRepositoryImplTest {

    @Test
    fun `getPlaybackUrl hits the exact endpoint and maps url and expiresAt verbatim, url still relative`() = runTest {
        var seenPath: String? = null
        val engine = MockEngine { request: HttpRequestData ->
            seenPath = request.url.encodedPath
            assertEquals(HttpMethod.Get, request.method)
            respond(
                content = """{"data":{"url":"/api/v1/media/m1/stream?token=abc.def.ghi","expiresAt":"2026-09-12T10:05:00Z"},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).getPlaybackUrl("m1")

        assertEquals("/api/v1/media/m1/playback-url", seenPath)
        require(result is ApiResult.Success)
        assertEquals("/api/v1/media/m1/stream?token=abc.def.ghi", result.data.url)
        assertEquals(Instant.parse("2026-09-12T10:05:00Z"), result.data.expiresAt)
    }

    @Test
    fun `getPlaybackUrl surfaces 403 as a typed distinguishable ForbiddenNotEnrolled failure`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":{"code":"FORBIDDEN_NOT_ENROLLED","message":"You are not enrolled in this course."},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.Forbidden,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).getPlaybackUrl("m1")

        require(result is ApiResult.Failure)
        assertEquals(ApiErrorCode.ForbiddenNotEnrolled, result.code)
        assertEquals(403, result.httpStatus)
    }

    @Test
    fun `getPlaybackUrl surfaces 404 as a typed distinguishable MediaNotFound failure`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":{"code":"MEDIA_NOT_FOUND","message":"The media was not found."},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.NotFound,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).getPlaybackUrl("not-a-lesson-video")

        require(result is ApiResult.Failure)
        assertEquals(ApiErrorCode.MediaNotFound, result.code)
        assertEquals(404, result.httpStatus)
    }
}
