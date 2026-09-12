package com.mentora.shared.domain.usecase.certificate

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.data.repository.certificate.CertificateRepository
import com.mentora.shared.domain.model.CertificateSummary

/**
 * `GET /api/v1/certificates`, paged. Scoped to the authenticated student automatically by the JWT
 * server-side — see [CertificateRepository.listCertificates]'s kdoc for why there is no user-id
 * parameter here.
 */
class ListCertificatesUseCase(private val repository: CertificateRepository) {
    suspend operator fun invoke(cursor: String? = null, limit: Int? = null): ApiResult<CursorPage<CertificateSummary>> =
        repository.listCertificates(cursor, limit)
}
