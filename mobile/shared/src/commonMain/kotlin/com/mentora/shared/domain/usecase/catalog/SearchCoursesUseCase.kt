package com.mentora.shared.domain.usecase.catalog

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.data.repository.catalog.CatalogRepository
import com.mentora.shared.data.repository.catalog.CourseFilters
import com.mentora.shared.domain.model.CourseSummary

/**
 * `GET /api/v1/courses` with [filters] plus [cursor]-based pagination. `?language=` is threaded
 * automatically by [CatalogRepository]'s implementation from the active UI locale — see
 * [CourseFilters]'s kdoc for why it is not a parameter here.
 */
class SearchCoursesUseCase(private val repository: CatalogRepository) {
    suspend operator fun invoke(
        filters: CourseFilters = CourseFilters(),
        cursor: String? = null,
    ): ApiResult<CursorPage<CourseSummary>> = repository.searchCourses(filters, cursor)
}
