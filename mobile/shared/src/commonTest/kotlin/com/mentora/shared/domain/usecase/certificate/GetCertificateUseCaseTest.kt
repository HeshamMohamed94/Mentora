package com.mentora.shared.domain.usecase.certificate

import com.mentora.shared.config.ApiEnvironment
import com.mentora.shared.data.network.ApiClient
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.HttpClientFactory
import com.mentora.shared.data.repository.certificate.CertificateRepositoryImpl
import com.mentora.shared.domain.model.CertificateDetail
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private val testEnvironment = ApiEnvironment.custom("http://localhost")

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

/** A realistic public certificate id shape — `MTR-` followed by 6 groups of 4 hex chars
 * (`CertificateService.publicId()`). Used only as a realistic fixture value; [GetCertificateUseCase]
 * and everything beneath it must never validate, parse, or reconstruct this shape. */
private const val REALISTIC_MTR_ID = "MTR-5F2A-9B10-4C3D-8E7F-1A2B-6C4D"

class GetCertificateUseCaseTest {

    @Test
    fun `invoke sends the given id byte-for-byte in the request URL with no reformatting`() = runTest {
        var seenUrl: String? = null
        val engine = MockEngine { request: HttpRequestData ->
            seenUrl = request.url.encodedPath
            respond(
                content = """{"data":{"id":"$REALISTIC_MTR_ID","studentNameSnapshot":"Alex Student","courseTitleSnapshot":"Kotlin Mastery","instructorNameSnapshot":"Jane Doe","completionDateSnapshot":"2026-03-01T00:00:00Z","issuedAt":"2026-03-01T00:05:00Z"},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val useCase = GetCertificateUseCase(CertificateRepositoryImpl(ApiClient(HttpClientFactory.create(engine, testEnvironment))))

        val result = useCase(REALISTIC_MTR_ID)

        assertEquals("/api/v1/certificates/$REALISTIC_MTR_ID", seenUrl)
        require(result is ApiResult.Success)
        // Every snapshot field maps through as-is — no derived/computed values.
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
    fun `invoke surfaces another student's certificate id as an ordinary 404 CERTIFICATE_NOT_FOUND failure`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":{"code":"CERTIFICATE_NOT_FOUND","message":"The certificate was not found."},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.NotFound,
                headers = jsonHeaders(),
            )
        }
        val useCase = GetCertificateUseCase(CertificateRepositoryImpl(ApiClient(HttpClientFactory.create(engine, testEnvironment))))

        val result = useCase(REALISTIC_MTR_ID)

        require(result is ApiResult.Failure)
        assertEquals(ApiErrorCode.CertificateNotFound, result.code)
        assertEquals(404, result.httpStatus)
    }
}
