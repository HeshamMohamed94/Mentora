package com.mentora.shared.domain.model

/**
 * `GET /api/v1/certificates/{id}`'s response — mirrors the backend's `CertificateDetailResponse`
 * (`backend/src/main/kotlin/com/mentora/backend/certificates/service/CertificateService.kt:30-37`)
 * field-for-field: `id, studentNameSnapshot, courseTitleSnapshot, instructorNameSnapshot,
 * completionDateSnapshot, issuedAt`.
 *
 * [id] is the same opaque public `MTR-...` code as [CertificateSummary.id] — see that type's kdoc.
 * `{id}` in the URL path is passed through completely verbatim by
 * [com.mentora.shared.data.repository.certificate.CertificateRepositoryImpl] — never validated
 * against the `MTR-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX` shape, never parsed, never reconstructed. Only
 * the backend (`CertificateService.parsePublicId()`) ever converts it back to an `ObjectId`.
 *
 * A certificate id belonging to another student resolves to an ordinary
 * `404 CERTIFICATE_NOT_FOUND` [com.mentora.shared.data.network.ApiResult.Failure] — verified from
 * `CertificateService.get()`: `repository.findByIdAndUser(parsePublicId(id), principal.userId) ?:
 * throw certificateNotFound()` (`CertificateService.kt:85-89`) scopes the lookup to the caller's own
 * `userId`, so another student's certificate id is indistinguishable from a nonexistent one. This is
 * a genuine error state (not a "no certificate yet" legitimate state like `QuizLookupResult.NoQuiz`)
 * — `shared` forwards it as an ordinary typed [com.mentora.shared.data.network.ApiResult.Failure],
 * never folding it into a fake `Success`.
 *
 * [studentNameSnapshot]/[courseTitleSnapshot]/[instructorNameSnapshot]/[completionDateSnapshot] are
 * ALL frozen at the moment of issuance and never re-resolved against live course/user data on a
 * later read. Verified directly from `CertificateService.kt`:
 * - Issuance (`checkAndIssueIfComplete()`, lines 57-72) resolves `instructor`/`student` names ONCE
 *   via `users.getProfile(...)` and writes them onto the `CertificateDocument` at insert time,
 *   alongside `course.title` and `now` (used for both `completionDateSnapshot` and `issuedAt`).
 * - Every read (`list()`/`get()`, via `toSummary()`/`toDetail()`, lines 111-117) maps the stored
 *   `CertificateDocument` fields straight onto the response DTO with zero additional queries to
 *   `CourseService`/`UserService` — if the course is later renamed or the instructor's account name
 *   changes, this certificate keeps showing exactly what it showed at issuance. `shared` mirrors this
 *   by never re-deriving/re-fetching these fields from [Course]/[CourseSummary]/[User] anywhere.
 *
 * [completionDateSnapshot]/[issuedAt] are kept as raw ISO-8601 wire strings, the same convention as
 * [User.createdAt] — `shared` has no `kotlinx-datetime` dependency yet.
 */
data class CertificateDetail(
    val id: String,
    val studentNameSnapshot: String,
    val courseTitleSnapshot: String,
    val instructorNameSnapshot: String,
    val completionDateSnapshot: String,
    val issuedAt: String,
)
