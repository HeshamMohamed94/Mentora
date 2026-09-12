package com.mentora.shared.domain.usecase.progress

import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.progress.ProgressRepository
import com.mentora.shared.domain.model.CourseProgress
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class FakeProgressRepositoryForGetProgress(private val progressResult: ApiResult<CourseProgress>) : ProgressRepository {
    var getProgressCallCount = 0
        private set

    override suspend fun getProgress(courseId: String): ApiResult<CourseProgress> {
        getProgressCallCount++
        return progressResult
    }

    override suspend fun completeLesson(courseId: String, lessonId: String): ApiResult<CourseProgress> = throw NotImplementedError()

    override suspend fun reportPosition(courseId: String, lessonId: String, positionSeconds: Int): ApiResult<CourseProgress> =
        throw NotImplementedError()
}

private val sampleProgress = CourseProgress(
    courseId = "c1", completedLessonIds = emptyList(), currentLessonId = null,
    currentPositionSeconds = null, quizPassed = null, completionPercent = 0, courseCompletedAt = null,
)

class GetCourseProgressUseCaseTest {

    @Test
    fun `invoke forwards the course id and returns the repository result unchanged`() = runTest {
        val repository = FakeProgressRepositoryForGetProgress(ApiResult.Success(sampleProgress))
        val useCase = GetCourseProgressUseCase(repository)

        val result = useCase("c1")

        assertEquals(ApiResult.Success(sampleProgress), result)
        assertEquals(1, repository.getProgressCallCount)
    }

    @Test
    fun `a non-enrolled caller surfaces as a typed distinguishable ForbiddenNotEnrolled failure`() = runTest {
        val failure = ApiResult.Failure(ApiErrorCode.ForbiddenNotEnrolled, "Not enrolled.", null, 403)
        val repository = FakeProgressRepositoryForGetProgress(failure)
        val useCase = GetCourseProgressUseCase(repository)

        val result = useCase("c1")

        assertEquals(failure, result)
        require(result is ApiResult.Failure)
        assertEquals(ApiErrorCode.ForbiddenNotEnrolled, result.code)
    }
}
