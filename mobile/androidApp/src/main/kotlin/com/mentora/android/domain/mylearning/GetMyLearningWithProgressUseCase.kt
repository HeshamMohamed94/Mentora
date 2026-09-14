package com.mentora.android.domain.mylearning

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.domain.model.CourseProgress
import com.mentora.shared.domain.model.MyLearningItem

/**
 * T12 — the G3 join (`execution/PHASE_4_ANDROID_PLAN.md` § 6 G3), implemented exactly where G3's
 * decision says it must live: in `androidApp`, layered on top of `shared`'s existing
 * `sdk.enrollment.getMyLearning()` (itself already an enrollment+course join, see
 * [com.mentora.shared.domain.usecase.enrollment.GetMyLearningUseCase]'s own kdoc) with one more
 * per-course `sdk.progress.getCourseProgress(courseId)` call layered on top — mirroring web's own
 * already-accepted N+1-at-demo-scale pattern for the identical data gap
 * (`web/src/lib/api/my-learning.ts`'s `useMyLearning`, D40/D37) rather than reopening `shared` for a
 * new aggregate use case.
 *
 * **Round trips, for one full [invoke] call with N enrollments:** 1 (or more, if the enrollment list
 * itself pages — [PageLimit] keeps that a single request at current seed-data scale, mirroring
 * `CourseDetailsViewModel`'s identical [CourseDetailsEnrollmentPageLimit][com.mentora.android.ui.coursedetails.CourseDetailsEnrollmentPageLimit]
 * precedent) call to `getMyLearning` (which itself performs N internal `GET /courses/{id}` calls
 * inside `shared` — invisible from here) + exactly N calls to `getCourseProgress`, one per enrollment.
 *
 * **Fully pages through [getMyLearning]'s own cursor** (never just the first page), the same
 * "page fully, don't trust page 1 alone" discipline `CourseDetailsViewModel.isEnrolledIn` already
 * established for G4.
 *
 * **Fails fast**, matching [com.mentora.shared.domain.usecase.enrollment.GetMyLearningUseCase]'s own
 * documented philosophy ("a 'my learning' list that silently dropped a row because of a transient
 * error would be worse than a clear, retriable failure") — the first [ApiResult.Failure] encountered,
 * from either lambda, is returned immediately as the whole join's result, with no partial-success
 * shape.
 */
class GetMyLearningWithProgressUseCase(
    private val getMyLearning: suspend (String?, Int?) -> ApiResult<CursorPage<MyLearningItem>>,
    private val getCourseProgress: suspend (String) -> ApiResult<CourseProgress>,
) {
    suspend operator fun invoke(): ApiResult<List<LearningItemWithProgress>> {
        val myLearningItems = mutableListOf<MyLearningItem>()
        var cursor: String? = null
        do {
            when (val page = getMyLearning(cursor, PageLimit)) {
                is ApiResult.Success -> {
                    myLearningItems += page.data.items
                    cursor = page.data.nextCursor
                }
                is ApiResult.Failure -> return page
            }
        } while (cursor != null)

        val itemsWithProgress = mutableListOf<LearningItemWithProgress>()
        for (item in myLearningItems) {
            when (val progressResult = getCourseProgress(item.course.id)) {
                is ApiResult.Success -> itemsWithProgress += LearningItemWithProgress(
                    enrollment = item.enrollment,
                    course = item.course,
                    progress = progressResult.data,
                )
                is ApiResult.Failure -> return progressResult
            }
        }
        return ApiResult.Success(itemsWithProgress)
    }

    private companion object {
        /** A generously high per-page size, same rationale as
         *  [com.mentora.android.ui.coursedetails.CourseDetailsEnrollmentPageLimit] — resolves in a
         *  single request in practice at current seed-data scale, while staying correct if a real
         *  account ever has more. */
        const val PageLimit: Int = 50
    }
}
