package com.mentora.shared.domain.usecase.enrollment

import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.data.repository.catalog.CatalogRepository
import com.mentora.shared.data.repository.catalog.CourseFilters
import com.mentora.shared.data.repository.enrollment.EnrollmentRepository
import com.mentora.shared.domain.model.Category
import com.mentora.shared.domain.model.ContentLanguage
import com.mentora.shared.domain.model.Course
import com.mentora.shared.domain.model.CourseLevel
import com.mentora.shared.domain.model.CourseStatus
import com.mentora.shared.domain.model.CourseSummary
import com.mentora.shared.domain.model.Enrollment
import com.mentora.shared.domain.model.EnrollmentCompletion
import com.mentora.shared.domain.model.EnrollmentSource
import com.mentora.shared.domain.model.EnrollmentStatus
import com.mentora.shared.domain.model.PriceDisplay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private fun enrollment(id: String, courseId: String) = Enrollment(
    id = id, courseId = courseId, source = EnrollmentSource.DemoCheckout,
    enrolledAt = "2026-01-01T00:00:00Z", status = EnrollmentStatus.Active,
)

private fun course(id: String, title: String) = Course(
    id = id, title = title, description = "desc", categoryId = "cat1",
    level = CourseLevel.Beginner, contentLanguage = ContentLanguage.English,
    priceDisplay = PriceDisplay(100, "USD"), thumbnailMediaId = null, status = CourseStatus.Published,
    ratingSeed = 4.5, instructorId = "i1", instructorName = "Jane", sections = emptyList(),
    translations = emptyMap(),
)

private class FakeEnrollmentRepositoryForMyLearning(
    private val listResult: ApiResult<CursorPage<Enrollment>>,
) : EnrollmentRepository {
    override suspend fun getCheckoutPreview(courseId: String) = throw NotImplementedError()
    override suspend fun completeCheckout(courseId: String): ApiResult<EnrollmentCompletion> = throw NotImplementedError()
    override suspend fun listEnrollments(cursor: String?, limit: Int?): ApiResult<CursorPage<Enrollment>> = listResult
}

/** Serves course details keyed by id from a map, deliberately NOT in request order, so a
 * position-based (rather than courseId-based) pairing bug in [GetMyLearningUseCase] would be
 * caught by the assertions below. */
private class FakeCatalogRepositoryForMyLearning(
    private val coursesById: Map<String, ApiResult<Course>>,
) : CatalogRepository {
    val requestedCourseIds = mutableListOf<String>()

    override suspend fun listCategories(): ApiResult<List<Category>> = throw NotImplementedError()

    override suspend fun searchCourses(
        filters: CourseFilters,
        cursor: String?,
        limit: Int?,
    ): ApiResult<CursorPage<CourseSummary>> = throw NotImplementedError()

    override suspend fun getCourseDetails(id: String): ApiResult<Course> {
        requestedCourseIds += id
        return requireNotNull(coursesById[id]) { "no fixture course for id $id" }
    }
}

class GetMyLearningUseCaseTest {

    @Test
    fun `invoke fetches one course detail per enrollment and pairs each by courseId`() = runTest {
        val enrollments = listOf(enrollment("e1", "c1"), enrollment("e2", "c2"), enrollment("e3", "c3"))
        val enrollmentRepository = FakeEnrollmentRepositoryForMyLearning(
            ApiResult.Success(CursorPage(items = enrollments, nextCursor = "cursor-2")),
        )
        // Deliberately keyed out of enrollment order to prove matching is by courseId, not position.
        val catalogRepository = FakeCatalogRepositoryForMyLearning(
            mapOf(
                "c3" to ApiResult.Success(course("c3", "Course Three")),
                "c1" to ApiResult.Success(course("c1", "Course One")),
                "c2" to ApiResult.Success(course("c2", "Course Two")),
            ),
        )
        val useCase = GetMyLearningUseCase(enrollmentRepository, catalogRepository)

        val result = useCase()

        require(result is ApiResult.Success)
        assertEquals(3, catalogRepository.requestedCourseIds.size)
        assertEquals(listOf("c1", "c2", "c3"), catalogRepository.requestedCourseIds)
        assertEquals("cursor-2", result.data.nextCursor)

        val byCourseId = result.data.items.associateBy { it.enrollment.courseId }
        assertEquals(3, byCourseId.size)
        assertEquals("e1", byCourseId.getValue("c1").enrollment.id)
        assertEquals("Course One", byCourseId.getValue("c1").course.title)
        assertEquals("e2", byCourseId.getValue("c2").enrollment.id)
        assertEquals("Course Two", byCourseId.getValue("c2").course.title)
        assertEquals("e3", byCourseId.getValue("c3").enrollment.id)
        assertEquals("Course Three", byCourseId.getValue("c3").course.title)
    }

    @Test
    fun `invoke returns the enrollment listing failure unchanged without fetching any course`() = runTest {
        val failure = ApiResult.Failure(ApiErrorCode.Unknown("NETWORK_ERROR"), "boom", null, 0)
        val enrollmentRepository = FakeEnrollmentRepositoryForMyLearning(failure)
        val catalogRepository = FakeCatalogRepositoryForMyLearning(emptyMap())
        val useCase = GetMyLearningUseCase(enrollmentRepository, catalogRepository)

        val result = useCase()

        assertEquals(failure, result)
        assertEquals(0, catalogRepository.requestedCourseIds.size)
    }

    @Test
    fun `invoke fails fast on the first per-course detail failure, not a partial success`() = runTest {
        val enrollments = listOf(enrollment("e1", "c1"), enrollment("e2", "c2"))
        val enrollmentRepository = FakeEnrollmentRepositoryForMyLearning(
            ApiResult.Success(CursorPage(items = enrollments, nextCursor = null)),
        )
        val courseFailure = ApiResult.Failure(ApiErrorCode.CourseNotFound, "The course was not found.", null, 404)
        val catalogRepository = FakeCatalogRepositoryForMyLearning(
            mapOf("c1" to ApiResult.Success(course("c1", "Course One")), "c2" to courseFailure),
        )
        val useCase = GetMyLearningUseCase(enrollmentRepository, catalogRepository)

        val result = useCase()

        assertEquals(courseFailure, result)
        assertEquals(listOf("c1", "c2"), catalogRepository.requestedCourseIds)
    }
}
