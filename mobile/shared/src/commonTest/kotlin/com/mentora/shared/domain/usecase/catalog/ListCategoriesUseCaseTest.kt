package com.mentora.shared.domain.usecase.catalog

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.data.repository.catalog.CatalogRepository
import com.mentora.shared.data.repository.catalog.CourseFilters
import com.mentora.shared.domain.model.Category
import com.mentora.shared.domain.model.Course
import com.mentora.shared.domain.model.CourseSummary
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class FakeCatalogRepositoryForListCategories(
    private val categoriesResult: ApiResult<List<Category>>,
) : CatalogRepository {
    var listCategoriesCallCount = 0
        private set

    override suspend fun listCategories(): ApiResult<List<Category>> {
        listCategoriesCallCount++
        return categoriesResult
    }

    override suspend fun searchCourses(
        filters: CourseFilters,
        cursor: String?,
        limit: Int?,
    ): ApiResult<CursorPage<CourseSummary>> = throw NotImplementedError()

    override suspend fun getCourseDetails(id: String): ApiResult<Course> = throw NotImplementedError()
}

class ListCategoriesUseCaseTest {

    @Test
    fun `invoke delegates to the repository and returns its result unchanged`() = runTest {
        val categories = listOf(Category("cat1", "Programming", "programming", 3))
        val repository = FakeCatalogRepositoryForListCategories(ApiResult.Success(categories))
        val useCase = ListCategoriesUseCase(repository)

        val result = useCase()

        assertEquals(ApiResult.Success(categories), result)
        assertEquals(1, repository.listCategoriesCallCount)
    }
}
