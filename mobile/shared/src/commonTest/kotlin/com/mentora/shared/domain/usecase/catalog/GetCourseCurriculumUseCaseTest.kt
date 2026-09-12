package com.mentora.shared.domain.usecase.catalog

import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.data.repository.catalog.CatalogRepository
import com.mentora.shared.data.repository.catalog.CourseFilters
import com.mentora.shared.domain.model.Category
import com.mentora.shared.domain.model.Course
import com.mentora.shared.domain.model.CourseSummary
import com.mentora.shared.domain.model.Lesson
import com.mentora.shared.domain.model.Section
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class FakeCatalogRepositoryForCurriculum(
    private val detailsResult: ApiResult<Course>,
) : CatalogRepository {
    var getCourseDetailsCallCount = 0
        private set

    override suspend fun listCategories(): ApiResult<List<Category>> = throw NotImplementedError()

    override suspend fun searchCourses(
        filters: CourseFilters,
        cursor: String?,
        limit: Int?,
    ): ApiResult<CursorPage<CourseSummary>> = throw NotImplementedError()

    override suspend fun getCourseDetails(id: String): ApiResult<Course> {
        getCourseDetailsCallCount++
        return detailsResult
    }
}

class GetCourseCurriculumUseCaseTest {

    @Test
    fun `invoke extracts sections from the course detail via exactly one fetch`() = runTest {
        val section = Section(
            sectionId = "s0", title = "Section A", order = 0,
            lessons = listOf(Lesson("l0", "Intro", "desc", 0, videoMediaId = "v0", resources = emptyList())),
        )
        val course = testCourse.copy(sections = listOf(section))
        val repository = FakeCatalogRepositoryForCurriculum(ApiResult.Success(course))
        val useCase = GetCourseCurriculumUseCase(repository)

        val result = useCase("c1")

        assertEquals(ApiResult.Success(listOf(section)), result)
        assertEquals(1, repository.getCourseDetailsCallCount, "must be a thin extraction, not a second network call")
    }

    @Test
    fun `invoke forwards a failure unchanged`() = runTest {
        val failure = ApiResult.Failure(ApiErrorCode.CourseNotFound, "not found", null, 404)
        val repository = FakeCatalogRepositoryForCurriculum(failure)
        val useCase = GetCourseCurriculumUseCase(repository)

        val result = useCase("c1")

        assertEquals(failure, result)
    }
}
