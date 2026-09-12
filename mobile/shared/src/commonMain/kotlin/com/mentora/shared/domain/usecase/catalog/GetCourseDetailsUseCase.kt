package com.mentora.shared.domain.usecase.catalog

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.catalog.CatalogRepository
import com.mentora.shared.domain.model.Course

/**
 * `GET /api/v1/courses/{id}`. A draft course this caller can't access surfaces as an ordinary
 * `ApiResult.Failure(ApiErrorCode.CourseNotFound)` — see [CatalogRepository.getCourseDetails]'s
 * kdoc for the enrolled-student carve-out (D64) this use case builds no special handling around.
 */
class GetCourseDetailsUseCase(private val repository: CatalogRepository) {
    suspend operator fun invoke(courseId: String): ApiResult<Course> = repository.getCourseDetails(courseId)
}
