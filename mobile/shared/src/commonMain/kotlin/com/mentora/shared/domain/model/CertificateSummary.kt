package com.mentora.shared.domain.model

/**
 * A single item of `GET /api/v1/certificates` — mirrors the backend's `CertificateSummaryResponse`
 * (`backend/src/main/kotlin/com/mentora/backend/certificates/service/CertificateService.kt:23-28`)
 * field-for-field: `id, courseTitleSnapshot, instructorNameSnapshot, issuedAt`.
 *
 * Named `CertificateSummary` (not the bare `Certificate`) alongside the paired [CertificateDetail] —
 * a deliberate departure from [CourseSummary]/`Course`'s bare-name-for-detail convention (Task 7),
 * because here BOTH shapes carry a `-Summary`/`-Detail` suffix on the wire-mirroring backend types
 * themselves (`CertificateSummaryResponse`/`CertificateDetailResponse`), so keeping both shared-side
 * names suffixed reads clearer than an asymmetric `Certificate`/`CertificateDetail` pair would
 * (`execution/PHASE_3_KMP_PLAN.md` Task 11 — naming left to implementation, documented here).
 *
 * [id] is the certificate's public `MTR-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX` code
 * (`CertificateService.kt`'s `publicId()`), never the underlying Mongo `ObjectId` — `shared` treats
 * it as a completely opaque string: never validated, reformatted, or reconstructed, only round-tripped
 * verbatim to `GET /api/v1/certificates/{id}`.
 *
 * [courseTitleSnapshot]/[instructorNameSnapshot] are frozen at issuance time — copied once from the
 * course/instructor at the moment `CertificateService.checkAndIssueIfComplete()` issues the
 * certificate (`CertificateService.kt:68-70`) and stored on the `CertificateDocument`. They are
 * NEVER re-resolved against live course/user data on a later read — see [CertificateDetail]'s kdoc
 * for the full verification of this from `CertificateService.toSummary()`/`toDetail()`.
 *
 * [issuedAt] is kept as the raw ISO-8601 wire string, the same convention as [User.createdAt] —
 * `shared` has no `kotlinx-datetime` dependency yet.
 */
data class CertificateSummary(
    val id: String,
    val courseTitleSnapshot: String,
    val instructorNameSnapshot: String,
    val issuedAt: String,
)
