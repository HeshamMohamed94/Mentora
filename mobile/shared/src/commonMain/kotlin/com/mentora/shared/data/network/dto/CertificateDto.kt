package com.mentora.shared.data.network.dto

import kotlinx.serialization.Serializable

/**
 * Wire shapes for `backend/src/main/kotlin/com/mentora/backend/certificates/{routes/CertificateRoutes.kt,
 * service/CertificateService.kt}`'s 2 endpoints, verified from source (not paraphrased). There is
 * NO issuance/revoke/regenerate endpoint anywhere in the backend — grep-verified against every
 * route under `certificates/` (only 2 `get`s exist, `CertificateRoutes.kt:18,22`) and against every
 * call site of `CertificateService.checkAndIssueIfComplete()` (only `ProgressRoutes.kt`/
 * `QuizRoutes.kt`, invoked as a side effect of lesson-completion/quiz-passing, never from a
 * client-callable mutating route). `shared` therefore exposes no "issue certificate" DTO/action.
 */

/** `GET /api/v1/certificates`'s per-item shape — mirrors `CertificateSummaryResponse`
 * (`CertificateService.kt:23-28`) exactly: `id, courseTitleSnapshot, instructorNameSnapshot,
 * issuedAt`. [id] is the opaque public `MTR-...` code — see
 * [com.mentora.shared.domain.model.CertificateSummary]'s kdoc. */
@Serializable
data class CertificateSummaryDto(
    val id: String,
    val courseTitleSnapshot: String,
    val instructorNameSnapshot: String,
    val issuedAt: String,
)

/** `GET /api/v1/certificates/{id}`'s response — mirrors `CertificateDetailResponse`
 * (`CertificateService.kt:30-37`) exactly: `id, studentNameSnapshot, courseTitleSnapshot,
 * instructorNameSnapshot, completionDateSnapshot, issuedAt`. All snapshot fields are frozen at
 * issuance — see [com.mentora.shared.domain.model.CertificateDetail]'s kdoc for the full
 * verification. */
@Serializable
data class CertificateDetailDto(
    val id: String,
    val studentNameSnapshot: String,
    val courseTitleSnapshot: String,
    val instructorNameSnapshot: String,
    val completionDateSnapshot: String,
    val issuedAt: String,
)
