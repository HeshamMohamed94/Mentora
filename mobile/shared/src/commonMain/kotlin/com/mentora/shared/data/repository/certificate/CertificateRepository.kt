package com.mentora.shared.data.repository.certificate

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.domain.model.CertificateDetail
import com.mentora.shared.domain.model.CertificateSummary

/**
 * The only certificate network surface `domain/usecase/certificate` use cases are allowed to depend
 * on — mirrors [com.mentora.shared.data.repository.quiz.QuizRepository]'s "interface + Impl" pattern
 * (`execution/PHASE_3_KMP_PLAN.md` Task 11).
 *
 * Deliberately exposes no issue/revoke/regenerate method — there is no such endpoint anywhere in
 * the backend (see `data/network/dto/CertificateDto.kt`'s kdoc for the grep verification).
 * Certificate issuance is a server-side side effect of lesson-completion/quiz-passing
 * (`CertificateService.checkAndIssueIfComplete()`), never a client-triggered action.
 *
 * Also exposes no share/download/export/PDF/image-generation/verification-URL method — Task 11's
 * plan marks all of that as UI-only (share/download) or explicitly future/out-of-scope (a public
 * verification URL, per `product/MVP_SCOPE.md § 3`).
 */
interface CertificateRepository {
    /**
     * `GET /api/v1/certificates` → the caller's own certificates, paged. Scoped to the authenticated
     * student automatically by the JWT server-side (`CertificateRoutes.kt:18-20`'s
     * `service.list(principal, ...)`) — no explicit user-id parameter here, on the wire, or on this
     * method's signature. No `?language=` — this was never one of the 4 reads that carry it
     * (`execution/PHASE_3_KMP_PLAN.md`'s D57/C3 list).
     */
    suspend fun listCertificates(cursor: String? = null, limit: Int? = null): ApiResult<CursorPage<CertificateSummary>>

    /**
     * `GET /api/v1/certificates/{id}` → [CertificateDetail]. [id] is passed through to the URL path
     * completely verbatim — never validated, reformatted, or reconstructed (see
     * [CertificateDetail]'s kdoc for why). A certificate id belonging to another student, or any
     * nonexistent id, resolves to an ordinary `404 CERTIFICATE_NOT_FOUND`
     * [ApiResult.Failure] — a genuine error, not folded into a fake success.
     */
    suspend fun getCertificate(id: String): ApiResult<CertificateDetail>
}
