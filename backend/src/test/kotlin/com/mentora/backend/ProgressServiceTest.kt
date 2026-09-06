package com.mentora.backend

import com.mentora.backend.common.MentoraPrincipal
import com.mentora.backend.common.Role
import com.mentora.backend.courses.service.CourseResponse
import com.mentora.backend.courses.service.CourseService
import com.mentora.backend.courses.service.LessonResponse
import com.mentora.backend.courses.service.PriceDisplayDto
import com.mentora.backend.courses.service.SectionResponse
import com.mentora.backend.enrollment.service.EnrollmentService
import com.mentora.backend.progress.repository.CompletionUpdate
import com.mentora.backend.progress.repository.ProgressDocument
import com.mentora.backend.progress.repository.ProgressRepository
import com.mentora.backend.progress.service.ProgressService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.bson.types.ObjectId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ProgressServiceTest {
    private val repository = mockk<ProgressRepository>()
    private val courses = mockk<CourseService>()
    private val enrollments = mockk<EnrollmentService>(relaxed = true)
    private val service = ProgressService(repository, courses, enrollments)
    private val courseId = ObjectId()
    private val principal = MentoraPrincipal(ObjectId(), Role.student)
    private val existingCompletionTime = Instant.fromEpochMilliseconds(1)

    @Test
    fun `zero lesson course keeps completion at zero`() = runBlocking {
        val course = mockk<CourseResponse>()
        val visibleLesson = SectionResponse(
            "section", "Section", 0,
            listOf(LessonResponse("requested", "Requested", "Description", 0, null, emptyList())),
        )
        every { course.sections } returnsMany listOf(listOf(visibleLesson), emptyList())
        coEvery { courses.get(courseId.toHexString(), principal) } returns course
        coEvery { repository.findOrCreate(principal.userId, courseId, any()) } returns progress(emptyMap(), 0)
        coEvery { repository.markComplete(any()) } answers {
            val update = firstArg<CompletionUpdate>()
            progress(update.completed, update.percentage, update.lessonId)
        }

        val response = service.complete(courseId.toHexString(), "requested", principal)

        assertEquals(0, response.completionPercent)
    }

    @Test
    fun `already completed lesson does not grow completed set or percentage`() = runBlocking {
        val response = complete(
            lessonIds = listOf("lesson-1"),
            courseLessonIds = listOf("lesson-1", "lesson-2"),
            lessonToComplete = "lesson-1",
        )

        assertEquals(listOf("lesson-1"), response.completedLessonIds)
        assertEquals(50, response.completionPercent)
    }

    @Test
    fun `completing last lesson reaches one hundred percent`() = runBlocking {
        val response = complete(
            lessonIds = listOf("lesson-1"),
            courseLessonIds = listOf("lesson-1", "lesson-2"),
            lessonToComplete = "lesson-2",
        )

        assertEquals(100, response.completionPercent)
        assertEquals(listOf("lesson-1", "lesson-2"), response.completedLessonIds)
    }

    @Test
    fun `partial completion truncates integer percentage`() = runBlocking {
        val response = complete(
            lessonIds = emptyList(),
            courseLessonIds = listOf("lesson-1", "lesson-2", "lesson-3"),
            lessonToComplete = "lesson-1",
        )

        assertEquals(33, response.completionPercent)
    }

    private suspend fun complete(
        lessonIds: List<String>,
        courseLessonIds: List<String>,
        lessonToComplete: String = "requested",
    ): com.mentora.backend.progress.service.ProgressResponse {
        val current = progress(lessonIds.associateWith { existingCompletionTime }, 0)
        coEvery { courses.get(courseId.toHexString(), principal) } returns course(courseLessonIds + lessonToComplete)
        coEvery { repository.findOrCreate(principal.userId, courseId, any()) } returns current
        coEvery { repository.markComplete(any()) } answers {
            val update = firstArg<CompletionUpdate>()
            progress(update.completed, update.percentage, update.lessonId)
        }
        return service.complete(courseId.toHexString(), lessonToComplete, principal)
    }

    private fun progress(completed: Map<String, Instant>, percentage: Int, currentLesson: String? = null) = ProgressDocument(
        userId = principal.userId, courseId = courseId, completedLessonIds = completed,
        currentLessonId = currentLesson, completionPercent = percentage, updatedAt = existingCompletionTime,
    )

    private fun course(lessonIds: List<String>) = CourseResponse(
        id = courseId.toHexString(), title = "Course", description = "Description",
        categoryId = ObjectId().toHexString(), level = "beginner", contentLanguage = "en",
        priceDisplay = PriceDisplayDto(0, "USD"), thumbnailMediaId = ObjectId().toHexString(),
        status = "published", ratingSeed = 4.5, instructorId = ObjectId().toHexString(),
        instructorName = "Test Instructor",
        sections = if (lessonIds.isEmpty()) emptyList() else listOf(SectionResponse(
            "section", "Section", 0,
            lessonIds.distinct().mapIndexed { index, id -> LessonResponse(id, id, "Description", index, null, emptyList()) },
        )),
    )
}
