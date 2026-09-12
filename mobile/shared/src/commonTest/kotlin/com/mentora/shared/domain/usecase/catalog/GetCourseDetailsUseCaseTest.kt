package com.mentora.shared.domain.usecase.catalog

import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.data.repository.catalog.CatalogRepository
import com.mentora.shared.data.repository.catalog.CourseFilters
import com.mentora.shared.domain.model.Category
import com.mentora.shared.domain.model.ContentLanguage
import com.mentora.shared.domain.model.Course
import com.mentora.shared.domain.model.CourseLevel
import com.mentora.shared.domain.model.CourseStatus
import com.mentora.shared.domain.model.CourseSummary
import com.mentora.shared.domain.model.PriceDisplay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

internal val testCourse = Course(
    id = "c1", title = "Kotlin Mastery", description = "Learn Kotlin", categoryId = "cat1",
    level = CourseLevel.Intermediate, contentLanguage = ContentLanguage.English,
    priceDisplay = PriceDisplay(4999, "USD"), thumbnailMediaId = "m1", status = CourseStatus.Published,
    ratingSeed = 4.7, instructorId = "i1", instructorName = "Jane Doe", sections = emptyList(),
    translations = emptyMap(),
)

private class FakeCatalogRepositoryForDetails(
    private val detailsResult: ApiResult<Course>,
) : CatalogRepository {
    var getCourseDetailsCallCount = 0
        private set
    var lastCourseId: String? = null

    override suspend fun listCategories(): ApiResult<List<Category>> = throw NotImplementedError()

    override suspend fun searchCourses(
        filters: CourseFilters,
        cursor: String?,
        limit: Int?,
    ): ApiResult<CursorPage<CourseSummary>> = throw NotImplementedError()

    override suspend fun getCourseDetails(id: String): ApiResult<Course> {
        getCourseDetailsCallCount++
        lastCourseId = id
        return detailsResult
    }
}

class GetCourseDetailsUseCaseTest {

    @Test
    fun `invoke forwards the course id and returns the repository result unchanged`() = runTest {
        val repository = FakeCatalogRepositoryForDetails(ApiResult.Success(testCourse))
        val useCase = GetCourseDetailsUseCase(repository)

        val result = useCase("c1")

        assertEquals(ApiResult.Success(testCourse), result)
        assertEquals(1, repository.getCourseDetailsCallCount)
        assertEquals("c1", repository.lastCourseId)
    }

    @Test
    fun `a draft-not-accessible course surfaces as an ordinary CourseNotFound failure`() = runTest {
        val failure = ApiResult.Failure(ApiErrorCode.CourseNotFound, "The course was not found.", null, 404)
        val repository = FakeCatalogRepositoryForDetails(failure)
        val useCase = GetCourseDetailsUseCase(repository)

        val result = useCase("c1")

        assertEquals(failure, result)
    }
}
