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

private class FakeCatalogRepositoryForComplete(private val courseResult: ApiResult<Course>) : CatalogRepository {
    var getCourseDetailsCallCount = 0
        private set

    override suspend fun listCategories(): ApiResult<List<Category>> = throw NotImplementedError()
    override suspend fun searchCourses(filters: CourseFilters, cursor: String?, limit: Int?): ApiResult<CursorPage<CourseSummary>> =
        throw NotImplementedError()

    override suspend fun getCourseDetails(id: String): ApiResult<Course> {
        getCourseDetailsCallCount++
        return courseResult
    }
}

private class FakeProgressRepositoryForComplete(private val completeResult: ApiResult<CourseProgress>) : ProgressRepository {
    var completeLessonCallCount = 0
        private set

    override suspend fun getProgress(courseId: String): ApiResult<CourseProgress> = throw NotImplementedError()

    override suspend fun completeLesson(courseId: String, lessonId: String): ApiResult<CourseProgress> {
        completeLessonCallCount++
        return completeResult
    }

    override suspend fun reportPosition(courseId: String, lessonId: String, positionSeconds: Int): ApiResult<CourseProgress> =
        throw NotImplementedError()
}

private fun progressAfter(completedLessonIds: List<String>) = CourseProgress(
    courseId = "c1", completedLessonIds = completedLessonIds, currentLessonId = completedLessonIds.lastOrNull(),
    currentPositionSeconds = null, quizPassed = null, completionPercent = completedLessonIds.size * 100 / 12, courseCompletedAt = null,
)

class CompleteLessonUseCaseTest {

    @Test
    fun `completing a lesson mid-section advances to the next lesson in the SAME section`() = runTest {
        val progressRepository = FakeProgressRepositoryForComplete(ApiResult.Success(progressAfter(listOf("l000", "l001"))))
        val catalogRepository = FakeCatalogRepositoryForComplete(ApiResult.Success(progressTestCourse()))
        val useCase = CompleteLessonUseCase(progressRepository, catalogRepository)

        val result = useCase("c1", "l001")

        require(result is ApiResult.Success)
        assertEquals(LessonProgressTarget.LessonTarget("l002", null), result.data.autoAdvanceTarget)
        assertEquals(1, progressRepository.completeLessonCallCount)
    }

    @Test
    fun `completing the LAST lesson of a section crosses the boundary to the first lesson of the NEXT section`() = runTest {
        val progressRepository = FakeProgressRepositoryForComplete(ApiResult.Success(progressAfter(listOf("l000", "l001", "l002", "l003"))))
        val catalogRepository = FakeCatalogRepositoryForComplete(ApiResult.Success(progressTestCourse()))
        val useCase = CompleteLessonUseCase(progressRepository, catalogRepository)

        val result = useCase("c1", "l003")

        require(result is ApiResult.Success)
        assertEquals(LessonProgressTarget.LessonTarget("l100", null), result.data.autoAdvanceTarget)
    }

    @Test
    fun `completing the very last lesson of the whole course returns CourseFinished, not a crash`() = runTest {
        val allButLast = listOf("l000", "l001", "l002", "l003", "l100", "l101", "l102", "l103", "l200", "l201", "l202")
        val progressRepository = FakeProgressRepositoryForComplete(ApiResult.Success(progressAfter(allButLast + "l203")))
        val catalogRepository = FakeCatalogRepositoryForComplete(ApiResult.Success(progressTestCourse()))
        val useCase = CompleteLessonUseCase(progressRepository, catalogRepository)

        val result = useCase("c1", "l203")

        require(result is ApiResult.Success)
        assertEquals(LessonProgressTarget.CourseFinished, result.data.autoAdvanceTarget)
    }

    @Test
    fun `calling it twice on the same already-complete lesson does not error`() = runTest {
        val progressRepository = FakeProgressRepositoryForComplete(ApiResult.Success(progressAfter(listOf("l000"))))
        val catalogRepository = FakeCatalogRepositoryForComplete(ApiResult.Success(progressTestCourse()))
        val useCase = CompleteLessonUseCase(progressRepository, catalogRepository)

        val first = useCase("c1", "l000")
        val second = useCase("c1", "l000")

        require(first is ApiResult.Success)
        require(second is ApiResult.Success)
        assertEquals(first.data.autoAdvanceTarget, second.data.autoAdvanceTarget)
        assertEquals(2, progressRepository.completeLessonCallCount)
    }

    @Test
    fun `a complete-lesson failure is forwarded and the curriculum is never even fetched`() = runTest {
        val failure = ApiResult.Failure(ApiErrorCode.LessonNotFound, "not found", null, 404)
        val progressRepository = FakeProgressRepositoryForComplete(failure)
        val catalogRepository = FakeCatalogRepositoryForComplete(ApiResult.Success(progressTestCourse()))
        val useCase = CompleteLessonUseCase(progressRepository, catalogRepository)

        val result = useCase("c1", "ghost")

        assertEquals(failure, result)
        assertEquals(0, catalogRepository.getCourseDetailsCallCount)
    }

    @Test
    fun `a curriculum-fetch failure after a successful complete is forwarded`() = runTest {
        val failure = ApiResult.Failure(ApiErrorCode.CourseNotFound, "not found", null, 404)
        val progressRepository = FakeProgressRepositoryForComplete(ApiResult.Success(progressAfter(listOf("l000"))))
        val catalogRepository = FakeCatalogRepositoryForComplete(failure)
        val useCase = CompleteLessonUseCase(progressRepository, catalogRepository)

        val result = useCase("c1", "l000")

        assertEquals(failure, result)
    }
}
