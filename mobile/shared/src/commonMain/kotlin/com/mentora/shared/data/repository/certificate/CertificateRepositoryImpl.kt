package com.mentora.shared.data.repository.certificate

import com.mentora.shared.data.network.ApiClient
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.data.network.dto.CertificateDetailDto
import com.mentora.shared.data.network.dto.CertificateSummaryDto
import com.mentora.shared.domain.model.CertificateDetail
import com.mentora.shared.domain.model.CertificateSummary

/**
 * The real [CertificateRepository]. [id] on [getCertificate] is interpolated directly into the URL
 * path with no encoding/normalization/validation step of its own beyond what
 * [com.mentora.shared.data.network.ApiClient]'s underlying Ktor `get(path)` call already does for
 * any path string — this class never inspects, reformats, or reconstructs the `MTR-...` shape.
 */
internal class CertificateRepositoryImpl(private val apiClient: ApiClient) : CertificateRepository {

    override suspend fun listCertificates(cursor: String?, limit: Int?): ApiResult<CursorPage<CertificateSummary>> {
        val queryParams = buildMap {
            cursor?.let { put("cursor", it) }
            limit?.let { put("limit", it.toString()) }
        }
        return when (val result = apiClient.getPage<CertificateSummaryDto>("/api/v1/certificates", queryParams)) {
            is ApiResult.Success -> ApiResult.Success(
                CursorPage(items = result.data.items.map { it.toDomain() }, nextCursor = result.data.nextCursor),
            )
            is ApiResult.Failure -> result
        }
    }

    override suspend fun getCertificate(id: String): ApiResult<CertificateDetail> =
        when (val result = apiClient.get<CertificateDetailDto>("/api/v1/certificates/$id")) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Failure -> result
        }

    private fun CertificateSummaryDto.toDomain(): CertificateSummary =
        CertificateSummary(id, courseTitleSnapshot, instructorNameSnapshot, issuedAt)

    private fun CertificateDetailDto.toDomain(): CertificateDetail = CertificateDetail(
        id, studentNameSnapshot, courseTitleSnapshot, instructorNameSnapshot, completionDateSnapshot, issuedAt,
    )
}
