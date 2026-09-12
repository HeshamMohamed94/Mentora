package com.mentora.shared.domain.usecase.catalog

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.catalog.CatalogRepository
import com.mentora.shared.domain.model.Section

/**
 * There is no dedicated curriculum endpoint on the backend (verified: no
 * `/api/v1/courses/{id}/curriculum` route exists in `CourseRoutes.kt`) — this is a thin extraction
 * over the SAME fetch [GetCourseDetailsUseCase] performs, never a second network call. Kept as its
 * own use case anyway because "I just want the curriculum" is a distinct enough caller intent
 * (e.g. a curriculum-preview screen that never needs `priceDisplay`/`instructorName`) to be worth
 * naming, per `execution/PHASE_3_KMP_PLAN.md` Task 7.
 */
class GetCourseCurriculumUseCase(private val repository: CatalogRepository) {
    suspend operator fun invoke(courseId: String): ApiResult<List<Section>> =
        when (val result = repository.getCourseDetails(courseId)) {
            is ApiResult.Success -> ApiResult.Success(result.data.sections)
            is ApiResult.Failure -> result
        }
}
