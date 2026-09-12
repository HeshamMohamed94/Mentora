package com.mentora.shared.domain.usecase.enrollment

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.data.repository.catalog.CatalogRepository
import com.mentora.shared.data.repository.enrollment.EnrollmentRepository
import com.mentora.shared.domain.model.MyLearningItem

/**
 * Composes `GET /enrollments` (paged, minimal shape — see
 * [com.mentora.shared.domain.model.Enrollment]'s kdoc) with one `GET /courses/{id}` detail fetch
 * per enrollment (reusing Task 7's [CatalogRepository.getCourseDetails]) to build the richer "my
 * learning" view neither endpoint returns alone. A deliberate, accepted N+1-at-seed-data-scale
 * tradeoff — same precedent as Phase 2's D40 — implemented exactly once here so Android/iOS never
 * each reimplement this composition differently (`execution/PHASE_3_KMP_PLAN.md` Task 8 AC #4).
 *
 * Each [MyLearningItem] pairs an enrollment with the course fetched for its OWN
 * [com.mentora.shared.domain.model.Enrollment.courseId] — matched by id, never by list position,
 * since the two calls are independent requests and nothing on the wire guarantees matching order.
 *
 * Fails fast: the first per-course [ApiResult.Failure] encountered is returned immediately as the
 * whole use case's result, with no partial-success shape — a "my learning" list that silently
 * dropped a row because of a transient error would be worse than a clear, retriable failure.
 */
class GetMyLearningUseCase(
    private val enrollmentRepository: EnrollmentRepository,
    private val catalogRepository: CatalogRepository,
) {
    suspend operator fun invoke(cursor: String? = null, limit: Int? = null): ApiResult<CursorPage<MyLearningItem>> {
        val enrollments = when (val result = enrollmentRepository.listEnrollments(cursor, limit)) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> return result
        }

        val items = mutableListOf<MyLearningItem>()
        for (enrollment in enrollments.items) {
            when (val courseResult = catalogRepository.getCourseDetails(enrollment.courseId)) {
                is ApiResult.Success -> items += MyLearningItem(enrollment = enrollment, course = courseResult.data)
                is ApiResult.Failure -> return courseResult
            }
        }
        return ApiResult.Success(CursorPage(items = items, nextCursor = enrollments.nextCursor))
    }
}
