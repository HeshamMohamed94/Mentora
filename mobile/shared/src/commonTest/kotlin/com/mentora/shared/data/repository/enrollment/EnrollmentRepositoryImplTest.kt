package com.mentora.shared.data.repository.enrollment

import com.mentora.shared.config.ApiEnvironment
import com.mentora.shared.data.network.ApiClient
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.HttpClientFactory
import com.mentora.shared.domain.model.CheckoutCourse
import com.mentora.shared.domain.model.CheckoutPreview
import com.mentora.shared.domain.model.EnrollmentSource
import com.mentora.shared.domain.model.EnrollmentStatus
import com.mentora.shared.domain.model.PriceDisplay
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

private fun repositoryFor(engine: MockEngine, locale: AppLocale = AppLocale.English): EnrollmentRepository {
    val apiClient = ApiClient(HttpClientFactory.create(engine, testEnvironment))
    return EnrollmentRepositoryImpl(apiClient, FakePreferenceStore(initialLocale = locale))
}

private fun enrollmentJson(id: String = "e1", courseId: String = "c1") =
    """{"id":"$id","courseId":"$courseId","source":"demoCheckout","enrolledAt":"2026-01-01T00:00:00Z","status":"active"}"""

class EnrollmentRepositoryImplTest {

    // ---- getCheckoutPreview ----

    @Test
    fun `getCheckoutPreview maps the CheckoutPreview response`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"data":{"course":{"id":"c1","title":"Kotlin Mastery","thumbnailMediaId":"m1"},"instructorName":"Jane Doe","priceDisplay":{"amount":4999,"currency":"USD"}},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).getCheckoutPreview("c1")

        require(result is ApiResult.Success)
        assertEquals(
            CheckoutPreview(
                course = CheckoutCourse("c1", "Kotlin Mastery", "m1"),
                instructorName = "Jane Doe",
                priceDisplay = PriceDisplay(4999, "USD"),
            ),
            result.data,
        )
    }

    @Test
    fun `getCheckoutPreview appends language from PreferenceStore`() = runTest {
        var seenQuery: String? = null
        val engine = MockEngine { request: HttpRequestData ->
            seenQuery = request.url.parameters["language"]
            respond(
                content = """{"data":{"course":{"id":"c1","title":"T","thumbnailMediaId":null},"instructorName":"Jane","priceDisplay":{"amount":100,"currency":"USD"}},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        repositoryFor(engine, locale = AppLocale.Arabic).getCheckoutPreview("c1")

        assertEquals("ar", seenQuery)
    }

    @Test
    fun `getCheckoutPreview surfaces a non-existent or unpublished course as 404 CourseNotFound`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":{"code":"COURSE_NOT_FOUND","message":"The course was not found."},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.NotFound,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).getCheckoutPreview("missing")

        require(result is ApiResult.Failure)
        assertEquals(ApiErrorCode.CourseNotFound, result.code)
        assertEquals(404, result.httpStatus)
    }

    // ---- completeCheckout: 201 first time vs 200 repeat, both Success ----

    @Test
    fun `completeCheckout maps a 201 first-time completion as Success with alreadyEnrolled false`() = runTest {
        val engine = MockEngine { request: HttpRequestData ->
            assertEquals(HttpMethod.Post, request.method)
            respond(
                content = """{"data":{"enrollment":${enrollmentJson()},"alreadyEnrolled":false},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.Created,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).completeCheckout("c1")

        require(result is ApiResult.Success)
        assertEquals(false, result.data.alreadyEnrolled)
        assertEquals("e1", result.data.enrollment.id)
        assertEquals("c1", result.data.enrollment.courseId)
        assertEquals(EnrollmentSource.DemoCheckout, result.data.enrollment.source)
        assertEquals(EnrollmentStatus.Active, result.data.enrollment.status)
    }

    @Test
    fun `completeCheckout maps a 200 idempotent repeat as Success with alreadyEnrolled true`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"data":{"enrollment":${enrollmentJson()},"alreadyEnrolled":true},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).completeCheckout("c1")

        require(result is ApiResult.Success)
        assertEquals(true, result.data.alreadyEnrolled)
    }

    // ---- listEnrollments: minimal shape, no embedded course detail, cursor paging ----

    @Test
    fun `listEnrollments maps minimal enrollment fields with no embedded course detail`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"data":[${enrollmentJson()}],"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).listEnrollments()

        require(result is ApiResult.Success)
        val enrollment = result.data.items.single()
        assertEquals("e1", enrollment.id)
        assertEquals("c1", enrollment.courseId)
        assertEquals(EnrollmentSource.DemoCheckout, enrollment.source)
        assertEquals("2026-01-01T00:00:00Z", enrollment.enrolledAt)
        assertEquals(EnrollmentStatus.Active, enrollment.status)
    }

    @Test
    fun `listEnrollments pages through two sequential responses via nextCursor`() = runTest {
        var requestCount = 0
        val engine = MockEngine { request: HttpRequestData ->
            requestCount++
            val cursor = request.url.parameters["cursor"]
            if (cursor == null) {
                respond(
                    content = """{"data":[${enrollmentJson("e1", "c1")}],"meta":{"requestId":"r1","nextCursor":"cursor-2"}}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders(),
                )
            } else {
                respond(
                    content = """{"data":[${enrollmentJson("e2", "c2")}],"meta":{"requestId":"r2"}}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders(),
                )
            }
        }
        val repository = repositoryFor(engine)

        val firstPage = repository.listEnrollments(cursor = null)
        require(firstPage is ApiResult.Success)
        assertEquals(listOf("e1"), firstPage.data.items.map { it.id })
        assertEquals("cursor-2", firstPage.data.nextCursor)

        val secondPage = repository.listEnrollments(cursor = firstPage.data.nextCursor)
        require(secondPage is ApiResult.Success)
        assertEquals(listOf("e2"), secondPage.data.items.map { it.id })
        assertNull(secondPage.data.nextCursor)

        assertEquals(2, requestCount)
    }

    @Test
    fun `listEnrollments never sends a language query parameter`() = runTest {
        var sawLanguage = false
        val engine = MockEngine { request: HttpRequestData ->
            sawLanguage = request.url.parameters.contains("language")
            respond(content = """{"data":[],"meta":{"requestId":"r1"}}""", status = HttpStatusCode.OK, headers = jsonHeaders())
        }

        repositoryFor(engine, locale = AppLocale.Arabic).listEnrollments()

        assertEquals(false, sawLanguage)
    }
}
