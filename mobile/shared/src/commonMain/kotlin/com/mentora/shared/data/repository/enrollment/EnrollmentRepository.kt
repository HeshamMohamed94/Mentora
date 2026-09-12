package com.mentora.shared.data.repository.enrollment

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.domain.model.CheckoutPreview
import com.mentora.shared.domain.model.Enrollment
import com.mentora.shared.domain.model.EnrollmentCompletion

/**
 * The only enrollment/demo-checkout network surface `domain/usecase/enrollment` use cases are
 * allowed to depend on — mirrors [com.mentora.shared.data.repository.catalog.CatalogRepository]'s
 * "interface + Impl" pattern (`execution/PHASE_3_KMP_PLAN.md` Task 8).
 */
interface EnrollmentRepository {
    /**
     * `GET /api/v1/courses/{id}/checkout` → [CheckoutPreview]. `?language=` is appended
     * automatically from the active UI locale via
     * [com.mentora.shared.data.network.localeQueryParam] — the same mechanism
     * [com.mentora.shared.data.repository.catalog.CatalogRepositoryImpl] uses (Task 7 AC #5,
     * `execution/DECISIONS_LOG.md` D73), never re-derived here. Student role required
     * server-side; 404s `COURSE_NOT_FOUND` for a non-existent or unpublished course id, never a
     * 403 — see [CheckoutPreview]'s kdoc.
     */
    suspend fun getCheckoutPreview(courseId: String): ApiResult<CheckoutPreview>

    /**
     * `POST /api/v1/courses/{id}/checkout/complete` → [EnrollmentCompletion]. Idempotent: a repeat
     * call for a course the caller is already enrolled in still returns an ordinary
     * [ApiResult.Success] (HTTP 200, `alreadyEnrolled = true`) rather than an error — see
     * [EnrollmentCompletion]'s kdoc. Never retried by `shared` itself — Task 3's `ApiClient` never
     * retries a mutating verb, and this repository adds no retry wrapper of its own.
     */
    suspend fun completeCheckout(courseId: String): ApiResult<EnrollmentCompletion>

    /**
     * `GET /api/v1/enrollments` → the caller's own enrollments, paged. Scoped to the authenticated
     * student automatically by the JWT server-side (`EnrollmentRoutes.kt:34-37`'s
     * `service.list(principal, ...)`) — there is no explicit user-id parameter here, on the wire,
     * or on this method's signature. No `?language=` — this endpoint was never one of the 4 reads
     * that carries it (`execution/PHASE_3_KMP_PLAN.md`'s D57/C3 list).
     */
    suspend fun listEnrollments(cursor: String? = null, limit: Int? = null): ApiResult<CursorPage<Enrollment>>
}
