package com.mentora.backend.aitutor

import com.mentora.backend.aitutor.provider.AiProvider
import com.mentora.backend.aitutor.provider.AiUsage
import com.mentora.backend.aitutor.repository.AiConversationDocument
import com.mentora.backend.aitutor.repository.AiMessageDocument
import com.mentora.backend.aitutor.repository.AiTutorRepository
import com.mentora.backend.aitutor.service.AiTutorService
import com.mentora.backend.aitutor.service.SendAiMessageRequest
import com.mentora.backend.common.ApiException
import com.mentora.backend.common.MentoraPrincipal
import com.mentora.backend.common.Page
import com.mentora.backend.common.Role
import com.mentora.backend.config.AppConfig
import com.mentora.backend.courses.service.CourseBrief
import com.mentora.backend.courses.service.CourseResponse
import com.mentora.backend.courses.service.CourseService
import com.mentora.backend.courses.service.LessonResponse
import com.mentora.backend.courses.service.PriceDisplayDto
import com.mentora.backend.courses.service.SectionResponse
import com.mentora.backend.enrollment.service.EnrollmentResponse
import com.mentora.backend.enrollment.service.EnrollmentService
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.bson.types.ObjectId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * PHASE_6_IMPLEMENTATION_PLAN.md § 3 T3 — service-level proof that the enrolled-course read is
 * unconditional (global AND lesson-context mode), scoped only to the principal's own enrollments,
 * and that a pre-stream provider failure never appends an assistant message.
 */
class AiTutorServiceTest {

    private val principal = MentoraPrincipal(ObjectId(), Role.student)
    private val repository = mockk<AiTutorRepository>()
    private val enrollment = mockk<EnrollmentService>()
    private val courses = mockk<CourseService>()
    private val provider = mockk<AiProvider>()
    private val appConfig = AppConfig(
        mongoUri = "mongodb://localhost:27017", mongoDatabaseName = "test",
        jwtSigningSecret = "fixed-test-signing-secret-at-least-32-bytes", jwtIssuer = "mentora-backend-test",
        accessTokenTtlMinutes = 15, refreshTokenTtlDays = 30, mediaStorageRoot = "storage/media",
        corsAllowedOrigins = emptyList(), aiProviderApiKey = null, aiProviderModel = "test-model",
        logLevel = "DEBUG", aiProviderMaxResponseTokens = 1024, aiProviderTimeoutSeconds = 120,
    )
    private val service = AiTutorService(repository, enrollment, courses, provider, appConfig)

    @Test
    fun `enrolled courses are fetched in global mode`() = runBlocking {
        stubConversationPlumbing()
        val enrolledCourseId = ObjectId()
        coEvery { enrollment.list(principal, any()) } returns
            Page(listOf(enrollmentResponse(enrolledCourseId)), null)
        coEvery { courses.enrolledCourseBriefs(any()) } returns listOf(CourseBrief("Kotlin Basics", "beginner"))
        coEvery { provider.complete(any(), any()) } returns AiUsage(null, null)

        service.streamMessage(principal, SendAiMessageRequest(content = "Hello"), "req-1") { }

        coVerify(exactly = 1) { enrollment.list(principal, any()) }
        coVerify(exactly = 1) { courses.enrolledCourseBriefs(listOf(enrolledCourseId)) }
    }

    @Test
    fun `enrolled courses are fetched in lesson-context mode too`() = runBlocking {
        stubConversationPlumbing()
        val enrolledCourseId = ObjectId()
        val lessonId = "lesson-1"
        coEvery { enrollment.requireEnrollment(principal.userId, enrolledCourseId) } returns Unit
        coEvery { courses.get(enrolledCourseId.toHexString(), principal) } returns courseResponse(enrolledCourseId, lessonId)
        coEvery { enrollment.list(principal, any()) } returns
            Page(listOf(enrollmentResponse(enrolledCourseId)), null)
        coEvery { courses.enrolledCourseBriefs(any()) } returns listOf(CourseBrief("Kotlin Basics", "beginner"))
        coEvery { provider.complete(any(), any()) } returns AiUsage(null, null)

        service.streamMessage(
            principal,
            SendAiMessageRequest(content = "Explain this", courseId = enrolledCourseId.toHexString(), lessonContextId = lessonId),
            "req-2",
        ) { }

        coVerify(exactly = 1) { enrollment.list(principal, any()) }
        coVerify(exactly = 1) { courses.enrolledCourseBriefs(listOf(enrolledCourseId)) }
    }

    @Test
    fun `id set passed to enrolledCourseBriefs traces only to the principal's own enrollments`() = runBlocking {
        stubConversationPlumbing()
        val ownCourseA = ObjectId()
        val ownCourseB = ObjectId()
        coEvery { enrollment.list(principal, any()) } returns
            Page(listOf(enrollmentResponse(ownCourseA), enrollmentResponse(ownCourseB)), null)
        val idsSlot: CapturingSlot<List<ObjectId>> = slot()
        coEvery { courses.enrolledCourseBriefs(capture(idsSlot)) } returns emptyList()
        coEvery { provider.complete(any(), any()) } returns AiUsage(null, null)

        service.streamMessage(principal, SendAiMessageRequest(content = "Hello"), "req-3") { }

        assertEquals(setOf(ownCourseA, ownCourseB), idsSlot.captured.toSet())
    }

    @Test
    fun `a pre-stream provider failure appends the user message but never an assistant message`() = runBlocking {
        stubConversationPlumbing()
        coEvery { enrollment.list(principal, any()) } returns Page(emptyList(), null)
        coEvery { courses.enrolledCourseBriefs(any()) } returns emptyList()
        coEvery { provider.complete(any(), any()) } throws ApiException.ServiceUnavailable()

        val error = runCatching {
            service.streamMessage(principal, SendAiMessageRequest(content = "Hello"), "req-4") { }
        }
        assertTrue(error.isFailure)

        coVerify(exactly = 1) { repository.append(match { it.role == "user" }) }
        coVerify(exactly = 0) { repository.append(match { it.role == "assistant" }) }
    }

    private fun stubConversationPlumbing() {
        val conversationId = ObjectId()
        coEvery { repository.findOrCreate(principal.userId, any()) } returns
            AiConversationDocument(id = conversationId, userId = principal.userId, createdAt = Clock.System.now())
        coEvery { repository.recent(conversationId, any()) } returns emptyList()
        coEvery { repository.append(any()) } answers { firstArg<AiMessageDocument>().copy(id = ObjectId()) }
    }

    private fun enrollmentResponse(courseId: ObjectId) = EnrollmentResponse(
        id = ObjectId().toHexString(), courseId = courseId.toHexString(), source = "demoCheckout",
        enrolledAt = Instant.fromEpochMilliseconds(1), status = "active",
    )

    private fun courseResponse(courseId: ObjectId, lessonId: String) = CourseResponse(
        id = courseId.toHexString(), title = "Kotlin Basics", description = "Description",
        categoryId = ObjectId().toHexString(), level = "beginner", contentLanguage = "en",
        priceDisplay = PriceDisplayDto(100, "USD"), thumbnailMediaId = null, status = "published",
        ratingSeed = 4.5, instructorId = ObjectId().toHexString(), instructorName = "Instructor",
        sections = listOf(
            SectionResponse(
                sectionId = "section-1", title = "Section", order = 0,
                lessons = listOf(
                    LessonResponse(
                        lessonId = lessonId, title = "Lesson", description = "Lesson description",
                        order = 0, videoMediaId = null, resources = emptyList(),
                    ),
                ),
            ),
        ),
    )
}
