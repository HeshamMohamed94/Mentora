package com.mentora.shared.data.repository.learningpath

import com.mentora.shared.config.ApiEnvironment
import com.mentora.shared.data.network.ApiClient
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.HttpClientFactory
import com.mentora.shared.domain.model.LearningPath
import com.mentora.shared.domain.model.LearningPathCourse
import com.mentora.shared.settings.AppLocale
import com.mentora.shared.settings.FakePreferenceStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val testEnvironment = ApiEnvironment.custom("http://localhost")

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

private fun repositoryFor(engine: MockEngine, locale: AppLocale = AppLocale.English): LearningPathRepository =
    LearningPathRepositoryImpl(ApiClient(HttpClientFactory.create(engine, testEnvironment)), FakePreferenceStore(initialLocale = locale))

class LearningPathRepositoryImplTest {

    // ---- listLearningPaths: plain unpaginated array, NOT getPage's cursor-page decoding ----

    @Test
    fun `listLearningPaths maps a plain unpaginated array response with no meta nextCursor expectation`() = runTest {
        var seenPath: String? = null
        val engine = MockEngine { request: HttpRequestData ->
            seenPath = request.url.encodedPath
            assertEquals(HttpMethod.Get, request.method)
            respond(
                content = """{"data":[{"id":"lp1","title":"Backend Path","description":"Server-side track","courseCount":3},{"id":"lp2","title":"Frontend Path","description":"Client-side track","courseCount":2}],"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).listLearningPaths()

        assertEquals("/api/v1/learning-paths", seenPath)
        require(result is ApiResult.Success)
        assertEquals(
            listOf(
                LearningPath("lp1", "Backend Path", "Server-side track", 3),
                LearningPath("lp2", "Frontend Path", "Client-side track", 2),
            ),
            result.data,
        )
    }

    @Test
    fun `listLearningPaths decodes a bare data array with no meta nextCursor present at all`() = runTest {
        // Deliberately no "nextCursor" key anywhere in "meta" — proves this decode path never
        // expects/relies on getPage's cursor-page shape.
        val engine = MockEngine {
            respond(
                content = """{"data":[{"id":"lp1","title":"Only Path","description":"d","courseCount":1}],"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).listLearningPaths()

        require(result is ApiResult.Success)
        assertEquals(1, result.data.size)
    }

    // ---- getLearningPath: guest vs authenticated-student ----

    @Test
    fun `getLearningPath maps a guest response with null progressPercent and false isFollowing`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"data":{"id":"lp1","title":"Backend Path","description":"Server-side track","courses":[{"id":"c1","title":"Kotlin Basics","thumbnailMediaId":"m1"},{"id":"c2","title":"Kotlin Advanced","thumbnailMediaId":null}],"progressPercent":null,"isFollowing":false},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).getLearningPath("lp1")

        require(result is ApiResult.Success)
        assertNull(result.data.progressPercent)
        assertEquals(false, result.data.isFollowing)
        assertEquals(
            listOf(
                LearningPathCourse("c1", "Kotlin Basics", "m1"),
                LearningPathCourse("c2", "Kotlin Advanced", null),
            ),
            result.data.courses,
        )
    }

    @Test
    fun `getLearningPath maps an authenticated-student response with a real progressPercent and isFollowing`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"data":{"id":"lp1","title":"Backend Path","description":"Server-side track","courses":[{"id":"c1","title":"Kotlin Basics","thumbnailMediaId":"m1"},{"id":"c2","title":"Kotlin Advanced","thumbnailMediaId":"m2"}],"progressPercent":50,"isFollowing":true},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).getLearningPath("lp1")

        require(result is ApiResult.Success)
        assertEquals(50, result.data.progressPercent)
        assertEquals(true, result.data.isFollowing)
    }

    @Test
    fun `getLearningPath surfaces a 404 LEARNING_PATH_NOT_FOUND as a typed distinguishable failure`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":{"code":"LEARNING_PATH_NOT_FOUND","message":"The learning path was not found."},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.NotFound,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).getLearningPath("missing")

        require(result is ApiResult.Failure)
        assertEquals(ApiErrorCode.LearningPathNotFound, result.code)
        assertEquals(404, result.httpStatus)
    }

    @Test
    fun `getLearningPath appends language=ar from PreferenceStore when the active locale is Arabic`() = runTest {
        var seenQuery: String? = null
        val engine = MockEngine { request: HttpRequestData ->
            seenQuery = request.url.parameters["language"]
            respond(
                content = """{"data":{"id":"lp1","title":"t","description":"d","courses":[],"progressPercent":null,"isFollowing":false},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        repositoryFor(engine, locale = AppLocale.Arabic).getLearningPath("lp1")

        assertEquals("ar", seenQuery)
    }

    @Test
    fun `getLearningPath appends language=en from PreferenceStore when the active locale is English`() = runTest {
        var seenQuery: String? = null
        val engine = MockEngine { request: HttpRequestData ->
            seenQuery = request.url.parameters["language"]
            respond(
                content = """{"data":{"id":"lp1","title":"t","description":"d","courses":[],"progressPercent":null,"isFollowing":false},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        repositoryFor(engine, locale = AppLocale.English).getLearningPath("lp1")

        assertEquals("en", seenQuery)
    }

    // ---- course order preservation: realistic curated (non-alphabetical, non-id-sorted) order ----

    @Test
    fun `getLearningPath preserves the curated course order exactly applying no client-side sort`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"data":{"id":"lp1","title":"Full Stack Path","description":"d","courses":[{"id":"c3","title":"Deployment Basics","thumbnailMediaId":null},{"id":"c1","title":"Advanced Kotlin","thumbnailMediaId":null},{"id":"c9","title":"Intro to Backend","thumbnailMediaId":null},{"id":"c2","title":"Zeroth Concepts","thumbnailMediaId":null}],"progressPercent":null,"isFollowing":false},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).getLearningPath("lp1")

        require(result is ApiResult.Success)
        // Curated order: c3, c1, c9, c2 — neither alphabetical by title nor sorted by id.
        assertEquals(listOf("c3", "c1", "c9", "c2"), result.data.courses.map { it.id })
    }

    // ---- follow / unfollow: round-trip + idempotency ----

    @Test
    fun `follow sends POST to the exact follow endpoint and maps isFollowing true`() = runTest {
        var seenPath: String? = null
        var seenMethod: HttpMethod? = null
        val engine = MockEngine { request: HttpRequestData ->
            seenPath = request.url.encodedPath
            seenMethod = request.method
            respond(
                content = """{"data":{"isFollowing":true},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).follow("lp1")

        assertEquals("/api/v1/learning-paths/lp1/follow", seenPath)
        assertEquals(HttpMethod.Post, seenMethod)
        require(result is ApiResult.Success)
        assertEquals(true, result.data)
    }

    @Test
    fun `unfollow sends DELETE to the exact follow endpoint and maps isFollowing false`() = runTest {
        var seenPath: String? = null
        var seenMethod: HttpMethod? = null
        val engine = MockEngine { request: HttpRequestData ->
            seenPath = request.url.encodedPath
            seenMethod = request.method
            respond(
                content = """{"data":{"isFollowing":false},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).unfollow("lp1")

        assertEquals("/api/v1/learning-paths/lp1/follow", seenPath)
        assertEquals(HttpMethod.Delete, seenMethod)
        require(result is ApiResult.Success)
        assertEquals(false, result.data)
    }

    @Test
    fun `follow twice in a row is idempotent with both calls succeeding with isFollowing true`() = runTest {
        var requestCount = 0
        val engine = MockEngine {
            requestCount++
            respond(
                content = """{"data":{"isFollowing":true},"meta":{"requestId":"r$requestCount"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val repository = repositoryFor(engine)

        val first = repository.follow("lp1")
        val second = repository.follow("lp1")

        require(first is ApiResult.Success)
        require(second is ApiResult.Success)
        assertEquals(true, first.data)
        assertEquals(true, second.data)
        assertEquals(2, requestCount)
    }

    @Test
    fun `unfollow twice in a row is idempotent with both calls succeeding with isFollowing false`() = runTest {
        var requestCount = 0
        val engine = MockEngine {
            requestCount++
            respond(
                content = """{"data":{"isFollowing":false},"meta":{"requestId":"r$requestCount"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val repository = repositoryFor(engine)

        val first = repository.unfollow("lp1")
        val second = repository.unfollow("lp1")

        require(first is ApiResult.Success)
        require(second is ApiResult.Success)
        assertEquals(false, first.data)
        assertEquals(false, second.data)
        assertEquals(2, requestCount)
    }
}
