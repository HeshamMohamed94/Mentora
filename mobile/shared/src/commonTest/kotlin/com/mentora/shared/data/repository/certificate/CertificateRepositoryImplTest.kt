package com.mentora.shared.data.repository.certificate

import com.mentora.shared.config.ApiEnvironment
import com.mentora.shared.data.network.ApiClient
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.HttpClientFactory
import com.mentora.shared.domain.model.CertificateDetail
import com.mentora.shared.domain.model.CertificateSummary
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

private fun repositoryFor(engine: MockEngine): CertificateRepository =
    CertificateRepositoryImpl(ApiClient(HttpClientFactory.create(engine, testEnvironment)))

private const val REALISTIC_MTR_ID = "MTR-1A2B-3C4D-5E6F-7A8B-9C0D-1E2F"

class CertificateRepositoryImplTest {

    // ---- listCertificates ----

    @Test
    fun `listCertificates hits the exact endpoint and maps every summary field verbatim`() = runTest {
        var seenPath: String? = null
        val engine = MockEngine { request: HttpRequestData ->
            seenPath = request.url.encodedPath
            assertEquals(HttpMethod.Get, request.method)
            respond(
                content = """{"data":[{"id":"$REALISTIC_MTR_ID","courseTitleSnapshot":"Kotlin Mastery","instructorNameSnapshot":"Jane Doe","issuedAt":"2026-03-01T00:00:00Z"}],"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).listCertificates()

        assertEquals("/api/v1/certificates", seenPath)
        require(result is ApiResult.Success)
        assertEquals(
            listOf(
                CertificateSummary(
                    id = REALISTIC_MTR_ID,
                    courseTitleSnapshot = "Kotlin Mastery",
                    instructorNameSnapshot = "Jane Doe",
                    issuedAt = "2026-03-01T00:00:00Z",
                ),
            ),
            result.data.items,
        )
    }

    @Test
    fun `listCertificates pages through two sequential responses via nextCursor`() = runTest {
        var requestCount = 0
        val engine = MockEngine { request: HttpRequestData ->
            requestCount++
            val cursor = request.url.parameters["cursor"]
            if (cursor == null) {
                respond(
                    content = """{"data":[{"id":"MTR-0000-0000-0000-0000-0000-0001","courseTitleSnapshot":"Course A","instructorNameSnapshot":"Instructor A","issuedAt":"2026-01-01T00:00:00Z"}],"meta":{"requestId":"r1","nextCursor":"cursor-2"}}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders(),
                )
            } else {
                respond(
                    content = """{"data":[{"id":"MTR-0000-0000-0000-0000-0000-0002","courseTitleSnapshot":"Course B","instructorNameSnapshot":"Instructor B","issuedAt":"2026-02-01T00:00:00Z"}],"meta":{"requestId":"r2"}}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders(),
                )
            }
        }
        val repository = repositoryFor(engine)

        val firstPage = repository.listCertificates(cursor = null)
        require(firstPage is ApiResult.Success)
        assertEquals(listOf("MTR-0000-0000-0000-0000-0000-0001"), firstPage.data.items.map { it.id })
        assertEquals("cursor-2", firstPage.data.nextCursor)

        val secondPage = repository.listCertificates(cursor = firstPage.data.nextCursor)
        require(secondPage is ApiResult.Success)
        assertEquals(listOf("MTR-0000-0000-0000-0000-0000-0002"), secondPage.data.items.map { it.id })
        assertNull(secondPage.data.nextCursor)

        assertEquals(2, requestCount)
    }

    // ---- getCertificate ----

    @Test
    fun `getCertificate sends the MTR id verbatim in the URL path with no reformatting`() = runTest {
        var seenPath: String? = null
        val engine = MockEngine { request: HttpRequestData ->
            seenPath = request.url.encodedPath
            assertEquals(HttpMethod.Get, request.method)
            respond(
                content = """{"data":{"id":"$REALISTIC_MTR_ID","studentNameSnapshot":"Alex Student","courseTitleSnapshot":"Kotlin Mastery","instructorNameSnapshot":"Jane Doe","completionDateSnapshot":"2026-03-01T00:00:00Z","issuedAt":"2026-03-01T00:05:00Z"},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).getCertificate(REALISTIC_MTR_ID)

        assertEquals("/api/v1/certificates/$REALISTIC_MTR_ID", seenPath)
        require(result is ApiResult.Success)
        assertEquals(
            CertificateDetail(
                id = REALISTIC_MTR_ID,
                studentNameSnapshot = "Alex Student",
                courseTitleSnapshot = "Kotlin Mastery",
                instructorNameSnapshot = "Jane Doe",
                completionDateSnapshot = "2026-03-01T00:00:00Z",
                issuedAt = "2026-03-01T00:05:00Z",
            ),
            result.data,
        )
    }

    @Test
    fun `getCertificate surfaces a 404 CERTIFICATE_NOT_FOUND as a typed distinguishable failure`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":{"code":"CERTIFICATE_NOT_FOUND","message":"The certificate was not found."},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.NotFound,
                headers = jsonHeaders(),
            )
        }

        // Simulates another student's certificate id: the backend's ownership-scoped lookup makes
        // this indistinguishable from a nonexistent id (`CertificateService.get()`).
        val result = repositoryFor(engine).getCertificate(REALISTIC_MTR_ID)

        require(result is ApiResult.Failure)
        assertEquals(ApiErrorCode.CertificateNotFound, result.code)
        assertEquals(404, result.httpStatus)
    }
}
