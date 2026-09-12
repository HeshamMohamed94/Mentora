package com.mentora.shared.domain.usecase.certificate

import com.mentora.shared.config.ApiEnvironment
import com.mentora.shared.data.network.ApiClient
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.HttpClientFactory
import com.mentora.shared.data.repository.certificate.CertificateRepositoryImpl
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
import kotlin.test.assertNull

private val testEnvironment = ApiEnvironment.custom("http://localhost")

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

/**
 * [ListCertificatesUseCase] is a thin pass-through onto
 * [com.mentora.shared.data.repository.certificate.CertificateRepository.listCertificates] — this
 * test therefore exercises the real [CertificateRepositoryImpl] against a [MockEngine], the same
 * "two pages via `MockEngine`" pattern `execution/PHASE_3_KMP_PLAN.md` Task 11 asks for (mirroring
 * Task 7/8's cursor-paging tests), rather than a fake repository.
 */
class ListCertificatesUseCaseTest {

    @Test
    fun `invoke pages through two sequential responses via nextCursor`() = runTest {
        var requestCount = 0
        val engine = MockEngine { request: HttpRequestData ->
            requestCount++
            val cursor = request.url.parameters["cursor"]
            if (cursor == null) {
                respond(
                    content = """{"data":[{"id":"MTR-AAAA-AAAA-AAAA-AAAA-AAAA-0001","courseTitleSnapshot":"Course A","instructorNameSnapshot":"Instructor A","issuedAt":"2026-01-01T00:00:00Z"}],"meta":{"requestId":"r1","nextCursor":"cursor-2"}}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders(),
                )
            } else {
                respond(
                    content = """{"data":[{"id":"MTR-AAAA-AAAA-AAAA-AAAA-AAAA-0002","courseTitleSnapshot":"Course B","instructorNameSnapshot":"Instructor B","issuedAt":"2026-02-01T00:00:00Z"}],"meta":{"requestId":"r2"}}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders(),
                )
            }
        }
        val useCase = ListCertificatesUseCase(CertificateRepositoryImpl(ApiClient(HttpClientFactory.create(engine, testEnvironment))))

        val firstPage = useCase(cursor = null)
        require(firstPage is ApiResult.Success)
        assertEquals(listOf("MTR-AAAA-AAAA-AAAA-AAAA-AAAA-0001"), firstPage.data.items.map { it.id })
        assertEquals("cursor-2", firstPage.data.nextCursor)

        val secondPage = useCase(cursor = firstPage.data.nextCursor)
        require(secondPage is ApiResult.Success)
        assertEquals(listOf("MTR-AAAA-AAAA-AAAA-AAAA-AAAA-0002"), secondPage.data.items.map { it.id })
        assertNull(secondPage.data.nextCursor)

        assertEquals(2, requestCount)
    }
}
