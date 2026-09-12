package com.mentora.shared.domain.usecase.progress

import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.data.repository.catalog.CatalogRepository
import com.mentora.shared.data.repository.catalog.CourseFilters
import com.mentora.shared.data.repository.progress.ProgressRepository
import com.mentora.shared.domain.model.Category
import com.mentora.shared.domain.model.Course
import com.mentora.shared.domain.model.CourseProgress
import com.mentora.shared.domain.model.CourseSummary
import com.mentora.shared.domain.model.LessonProgressTarget
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class FakeCatalogRepositoryForResume(private val courseResult: ApiResult<Course>) : CatalogRepository {
    override suspend fun listCategories(): ApiResult<List<Category>> = throw NotImplementedError()
    override suspend fun searchCourses(filters: CourseFilters, cursor: String?, limit: Int?): ApiResult<CursorPage<CourseSummary>> =
        throw NotImplementedError()
    override suspend fun getCourseDetails(id: String): ApiResult<Course> = courseResult
}

private class FakeProgressRepositoryForResume(private val progressResult: ApiResult<CourseProgress>) : ProgressRepository {
    override suspend fun getProgress(courseId: String): ApiResult<CourseProgress> = progressResult
    override suspend fun completeLesson(courseId: String, lessonId: String): ApiResult<CourseProgress> = throw NotImplementedError()
    override suspend fun reportPosition(courseId: String, lessonId: String, positionSeconds: Int): ApiResult<CourseProgress> =
        throw NotImplementedError()
}

private fun progress(
    completedLessonIds: List<String> = emptyList(),
    currentLessonId: String? = null,
    currentPositionSeconds: Int? = null,
) = CourseProgress(
    courseId = "c1", completedLessonIds = completedLessonIds, currentLessonId = currentLessonId,
    currentPositionSeconds = currentPositionSeconds, quizPassed = null, completionPercent = 0, courseCompletedAt = null,
)

private val allTwelveLessonIds = listOf(
    "l000", "l001", "l002", "l003", "l100", "l101", "l102", "l103", "l200", "l201", "l202", "l203",
)

class ResumeCourseUseCaseTest {

    @Test
    fun `zero progress on a brand new enrollment resolves to the very first lesson overall`() = runTest {
        val useCase = ResumeCourseUseCase(
            FakeCatalogRepositoryForResume(ApiResult.Success(progressTestCourse())),
            FakeProgressRepositoryForResume(ApiResult.Success(progress())),
        )

        val result = useCase("c1")

        assertEquals(ApiResult.Success(LessonProgressTarget.LessonTarget("l000", null)), result)
    }

    @Test
    fun `a valid currentLessonId mid-course resumes there with its currentPositionSeconds`() = runTest {
        val useCase = ResumeCourseUseCase(
            FakeCatalogRepositoryForResume(ApiResult.Success(progressTestCourse())),
            FakeProgressRepositoryForResume(
                ApiResult.Success(
                    progress(completedLessonIds = listOf("l000", "l001"), currentLessonId = "l101", currentPositionSeconds = 42),
                ),
            ),
        )

        val result = useCase("c1")

        assertEquals(ApiResult.Success(LessonProgressTarget.LessonTarget("l101", 42)), result)
    }

    @Test
    fun `a stale currentLessonId no longer in the curriculum falls back to the first incomplete lesson`() = runTest {
        val useCase = ResumeCourseUseCase(
            FakeCatalogRepositoryForResume(ApiResult.Success(progressTestCourse())),
            FakeProgressRepositoryForResume(
                ApiResult.Success(
                    progress(completedLessonIds = listOf("l000", "l001"), currentLessonId = "removed-lesson", currentPositionSeconds = 99),
                ),
            ),
        )

        val result = useCase("c1")

        assertEquals(ApiResult.Success(LessonProgressTarget.LessonTarget("l002", null)), result)
    }

    @Test
    fun `all lessons complete resolves to CourseFinished`() = runTest {
        val useCase = ResumeCourseUseCase(
            FakeCatalogRepositoryForResume(ApiResult.Success(progressTestCourse())),
            FakeProgressRepositoryForResume(ApiResult.Success(progress(completedLessonIds = allTwelveLessonIds))),
        )

        val result = useCase("c1")

        assertEquals(ApiResult.Success(LessonProgressTarget.CourseFinished), result)
    }

    @Test
    fun `a course-details failure is forwarded and progress is never even needed`() = runTest {
        val failure = ApiResult.Failure(ApiErrorCode.CourseNotFound, "not found", null, 404)
        val useCase = ResumeCourseUseCase(
            FakeCatalogRepositoryForResume(failure),
            FakeProgressRepositoryForResume(ApiResult.Success(progress())),
        )

        val result = useCase("c1")

        assertEquals(failure, result)
    }

    @Test
    fun `a progress-fetch failure is forwarded`() = runTest {
        val failure = ApiResult.Failure(ApiErrorCode.ForbiddenNotEnrolled, "not enrolled", null, 403)
        val useCase = ResumeCourseUseCase(
            FakeCatalogRepositoryForResume(ApiResult.Success(progressTestCourse())),
            FakeProgressRepositoryForResume(failure),
        )

        val result = useCase("c1")

        assertEquals(failure, result)
    }
}
