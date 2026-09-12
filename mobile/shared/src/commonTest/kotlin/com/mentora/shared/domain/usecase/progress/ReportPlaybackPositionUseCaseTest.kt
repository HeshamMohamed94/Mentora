package com.mentora.shared.domain.usecase.progress

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.progress.ProgressRepository
import com.mentora.shared.domain.model.CourseProgress
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

private class FakeProgressRepositoryForHeartbeat : ProgressRepository {
    var reportPositionCallCount = 0
        private set
    var lastPositionSeconds: Int? = null
        private set

    override suspend fun getProgress(courseId: String): ApiResult<CourseProgress> = throw NotImplementedError()
    override suspend fun completeLesson(courseId: String, lessonId: String): ApiResult<CourseProgress> = throw NotImplementedError()

    override suspend fun reportPosition(courseId: String, lessonId: String, positionSeconds: Int): ApiResult<CourseProgress> {
        reportPositionCallCount++
        lastPositionSeconds = positionSeconds
        return ApiResult.Success(
            CourseProgress(
                courseId = courseId, completedLessonIds = emptyList(), currentLessonId = lessonId,
                currentPositionSeconds = positionSeconds, quizPassed = null, completionPercent = 0, courseCompletedAt = null,
            ),
        )
    }
}

class ReportPlaybackPositionUseCaseTest {

    @Test
    fun `the very first call always goes through immediately`() = runTest {
        val repository = FakeProgressRepositoryForHeartbeat()
        val timeSource = TestTimeSource()
        val useCase = ReportPlaybackPositionUseCase(repository, timeSource, throttleWindow = 5.seconds)

        val result = useCase("c1", "l001", 10)

        require(result is ApiResult.Success)
        assertEquals(1, repository.reportPositionCallCount)
    }

    @Test
    fun `a rapid burst within the throttle window results in far fewer network calls than attempts`() = runTest {
        val repository = FakeProgressRepositoryForHeartbeat()
        val timeSource = TestTimeSource()
        val useCase = ReportPlaybackPositionUseCase(repository, timeSource, throttleWindow = 5.seconds)

        val results = (1..10).map { attempt ->
            timeSource += 200.milliseconds
            useCase("c1", "l001", attempt)
        }

        assertEquals(1, repository.reportPositionCallCount, "only the first attempt should have actually gone through")
        assertEquals(1, results.count { it != null })
        assertEquals(9, results.count { it == null })
    }

    @Test
    fun `calls spaced beyond the throttle window each go through`() = runTest {
        val repository = FakeProgressRepositoryForHeartbeat()
        val timeSource = TestTimeSource()
        val useCase = ReportPlaybackPositionUseCase(repository, timeSource, throttleWindow = 5.seconds)

        val first = useCase("c1", "l001", 1)
        timeSource += 5.seconds
        val second = useCase("c1", "l001", 2)
        timeSource += 5.seconds
        val third = useCase("c1", "l001", 3)

        require(first != null)
        require(second != null)
        require(third != null)
        assertEquals(3, repository.reportPositionCallCount)
        assertEquals(3, repository.lastPositionSeconds)
    }

    @Test
    fun `a call exactly one tick short of the window is still throttled`() = runTest {
        val repository = FakeProgressRepositoryForHeartbeat()
        val timeSource = TestTimeSource()
        val useCase = ReportPlaybackPositionUseCase(repository, timeSource, throttleWindow = 5.seconds)

        useCase("c1", "l001", 1)
        timeSource += 4.seconds
        val throttled = useCase("c1", "l001", 2)

        assertNull(throttled)
        assertEquals(1, repository.reportPositionCallCount)
    }
}
