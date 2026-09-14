package com.mentora.android.domain.mylearning

import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.domain.model.ContentLanguage
import com.mentora.shared.domain.model.Course
import com.mentora.shared.domain.model.CourseLevel
import com.mentora.shared.domain.model.CourseProgress
import com.mentora.shared.domain.model.CourseStatus
import com.mentora.shared.domain.model.Enrollment
import com.mentora.shared.domain.model.EnrollmentSource
import com.mentora.shared.domain.model.EnrollmentStatus
import com.mentora.shared.domain.model.MyLearningItem
import com.mentora.shared.domain.model.PriceDisplay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun course(id: String) = Course(
    id = id,
    title = "Course $id",
    description = "Description $id",
    categoryId = "cat-1",
    level = CourseLevel.Beginner,
    contentLanguage = ContentLanguage.English,
    priceDisplay = PriceDisplay(amount = 899, currency = "EGP"),
    thumbnailMediaId = null,
    status = CourseStatus.Published,
    ratingSeed = 4.5,
    instructorId = "instructor-1",
    instructorName = "Instructor",
    sections = emptyList(),
    translations = emptyMap(),
)

private fun enrollment(courseId: String) = Enrollment(
    id = "enrollment-$courseId",
    courseId = courseId,
    source = EnrollmentSource.DemoCheckout,
    enrolledAt = "2026-01-01T00:00:00Z",
    status = EnrollmentStatus.Active,
)

private fun progress(courseId: String, percent: Int = 50) = CourseProgress(
    courseId = courseId,
    completedLessonIds = emptyList(),
    currentLessonId = null,
    currentPositionSeconds = null,
    quizPassed = null,
    completionPercent = percent,
    courseCompletedAt = null,
)

/**
 * T12 — [GetMyLearningWithProgressUseCase]'s pagination/join/fail-fast logic, as a plain JVM unit
 * test. See that class's own kdoc for the G3 round-trip shape this asserts.
 */
class GetMyLearningWithProgressUseCaseTest {

    @Test
    fun joinsEachEnrollmentWithItsOwnProgress() = runBlocking {
        val useCase = GetMyLearningWithProgressUseCase(
            getMyLearning = { _, _ ->
                ApiResult.Success(
                    CursorPage(
                        items = listOf(
                            MyLearningItem(enrollment("course-1"), course("course-1")),
                            MyLearningItem(enrollment("course-2"), course("course-2")),
                        ),
                        nextCursor = null,
                    ),
                )
            },
            getCourseProgress = { courseId -> ApiResult.Success(progress(courseId, percent = if (courseId == "course-1") 40 else 100)) },
        )

        val result = useCase()

        val success = result as ApiResult.Success
        assertEquals(2, success.data.size)
        assertEquals(40, success.data.first { it.course.id == "course-1" }.progress.completionPercent)
        assertEquals(100, success.data.first { it.course.id == "course-2" }.progress.completionPercent)
    }

    @Test
    fun pagesFullyThroughGetMyLearningsOwnCursor() = runBlocking {
        val recordedCursors = mutableListOf<String?>()
        val useCase = GetMyLearningWithProgressUseCase(
            getMyLearning = { cursor, _ ->
                recordedCursors += cursor
                if (cursor == null) {
                    ApiResult.Success(CursorPage(items = listOf(MyLearningItem(enrollment("course-1"), course("course-1"))), nextCursor = "cursor-2"))
                } else {
                    ApiResult.Success(CursorPage(items = listOf(MyLearningItem(enrollment("course-2"), course("course-2"))), nextCursor = null))
                }
            },
            getCourseProgress = { courseId -> ApiResult.Success(progress(courseId)) },
        )

        val result = useCase() as ApiResult.Success

        assertEquals(listOf(null, "cursor-2"), recordedCursors)
        assertEquals(2, result.data.size)
    }

    @Test
    fun getMyLearningFailure_isReturnedImmediately_neverCallingGetCourseProgress() = runBlocking {
        var progressCallCount = 0
        val useCase = GetMyLearningWithProgressUseCase(
            getMyLearning = { _, _ -> ApiResult.Failure(ApiErrorCode.AuthTokenInvalid, "no session", null, 401) },
            getCourseProgress = { courseId -> progressCallCount++; ApiResult.Success(progress(courseId)) },
        )

        val result = useCase()

        assertTrue(result is ApiResult.Failure)
        assertEquals(ApiErrorCode.AuthTokenInvalid, (result as ApiResult.Failure).code)
        assertEquals(0, progressCallCount)
    }

    @Test
    fun oneCourseProgressFailure_failsTheWholeJoin_perGetMyLearningUseCasesOwnFailFastPhilosophy() = runBlocking {
        val useCase = GetMyLearningWithProgressUseCase(
            getMyLearning = { _, _ ->
                ApiResult.Success(
                    CursorPage(
                        items = listOf(
                            MyLearningItem(enrollment("course-1"), course("course-1")),
                            MyLearningItem(enrollment("course-2"), course("course-2")),
                        ),
                        nextCursor = null,
                    ),
                )
            },
            getCourseProgress = { courseId ->
                if (courseId == "course-2") {
                    ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500)
                } else {
                    ApiResult.Success(progress(courseId))
                }
            },
        )

        val result = useCase()

        assertTrue(result is ApiResult.Failure)
        assertEquals(ApiErrorCode.InternalError, (result as ApiResult.Failure).code)
    }

    @Test
    fun noEnrollments_returnsAnEmptyList() = runBlocking {
        val useCase = GetMyLearningWithProgressUseCase(
            getMyLearning = { _, _ -> ApiResult.Success(CursorPage(items = emptyList(), nextCursor = null)) },
            getCourseProgress = { courseId -> ApiResult.Success(progress(courseId)) },
        )

        val result = useCase() as ApiResult.Success

        assertEquals(emptyList<LearningItemWithProgress>(), result.data)
    }
}
