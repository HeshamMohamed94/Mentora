package com.mentora.shared.domain.usecase.certificate

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.certificate.CertificateRepository
import com.mentora.shared.domain.model.CertificateDetail

/**
 * `GET /api/v1/certificates/{id}`. Unlike
 * [com.mentora.shared.domain.usecase.quiz.GetQuizUseCase]'s `QuizLookupResult`/`NoQuiz` pattern,
 * this use case does NOT fold `404 CERTIFICATE_NOT_FOUND` into a sealed "legitimate empty state" —
 * a certificate id belonging to another student (or any nonexistent id) is a genuine error, not an
 * expected state analogous to "this course has no quiz yet." It is forwarded unchanged as an
 * ordinary [ApiResult.Failure], the same way
 * [com.mentora.shared.data.repository.catalog.CatalogRepositoryImpl]'s draft-course 404 is
 * forwarded unchanged.
 *
 * [id] is passed straight through to [CertificateRepository.getCertificate] with zero validation,
 * reformatting, or reconstruction — see [CertificateDetail]'s kdoc for why.
 */
class GetCertificateUseCase(private val repository: CertificateRepository) {
    suspend operator fun invoke(id: String): ApiResult<CertificateDetail> = repository.getCertificate(id)
}
