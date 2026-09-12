package com.mentora.shared.domain.usecase.catalog

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.data.repository.catalog.CatalogRepository
import com.mentora.shared.data.repository.catalog.CourseFilters
import com.mentora.shared.domain.model.Category
import com.mentora.shared.domain.model.Course
import com.mentora.shared.domain.model.CourseLevel
import com.mentora.shared.domain.model.CourseSummary
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class FakeCatalogRepositoryForSearch(
    private val searchResult: ApiResult<CursorPage<CourseSummary>>,
) : CatalogRepository {
    var lastFilters: CourseFilters? = null
    var lastCursor: String? = null

    override suspend fun listCategories(): ApiResult<List<Category>> = throw NotImplementedError()

    override suspend fun searchCourses(
        filters: CourseFilters,
        cursor: String?,
        limit: Int?,
    ): ApiResult<CursorPage<CourseSummary>> {
        lastFilters = filters
        lastCursor = cursor
        return searchResult
    }

    override suspend fun getCourseDetails(id: String): ApiResult<Course> = throw NotImplementedError()
}

class SearchCoursesUseCaseTest {

    @Test
    fun `invoke forwards filters and cursor to the repository and returns its result unchanged`() = runTest {
        val page = CursorPage(items = emptyList<CourseSummary>(), nextCursor = null)
        val repository = FakeCatalogRepositoryForSearch(ApiResult.Success(page))
        val useCase = SearchCoursesUseCase(repository)
        val filters = CourseFilters(level = CourseLevel.Advanced)

        val result = useCase(filters, cursor = "cursor-1")

        assertEquals(ApiResult.Success(page), result)
        assertEquals(filters, repository.lastFilters)
        assertEquals("cursor-1", repository.lastCursor)
    }

    @Test
    fun `invoke defaults to empty filters and a null cursor`() = runTest {
        val page = CursorPage(items = emptyList<CourseSummary>(), nextCursor = null)
        val repository = FakeCatalogRepositoryForSearch(ApiResult.Success(page))
        val useCase = SearchCoursesUseCase(repository)

        useCase()

        assertEquals(CourseFilters(), repository.lastFilters)
        assertEquals(null, repository.lastCursor)
    }
}
