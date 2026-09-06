package com.mentora.backend

import com.mentora.backend.categories.service.CategoryService
import com.mentora.backend.common.ApiException
import com.mentora.backend.common.MentoraPrincipal
import com.mentora.backend.common.Role
import com.mentora.backend.courses.repository.CourseDocument
import com.mentora.backend.courses.repository.CourseRepository
import com.mentora.backend.courses.repository.Lesson
import com.mentora.backend.courses.repository.PriceDisplay
import com.mentora.backend.courses.repository.Section
import com.mentora.backend.courses.service.CourseService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.bson.types.ObjectId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CourseServiceTest {
    private val repository = mockk<CourseRepository>()
    private val categories = mockk<CategoryService>(relaxed = true)
    private val service = CourseService(repository, categories)
    private val instructorId = ObjectId()
    private val principal = MentoraPrincipal(instructorId, Role.instructor)

    @Test fun `blank title is reported by publish validation`() = assertInvalid(
        readyCourse().copy(title = " "), mapOf("title" to "REQUIRED"),
    )

    @Test fun `blank description is reported by publish validation`() = assertInvalid(
        readyCourse().copy(description = " "), mapOf("description" to "REQUIRED"),
    )

    @Test fun `blank persisted category id is reported by publish validation`() {
        val invalidCategory = mockk<ObjectId>()
        every { invalidCategory.toHexString() } returns ""
        assertInvalid(readyCourse().copy(categoryId = invalidCategory), mapOf("categoryId" to "REQUIRED"))
    }

    @Test fun `negative price is reported by publish validation`() = assertInvalid(
        readyCourse().copy(priceDisplay = PriceDisplay(-1, "USD")), mapOf("priceDisplay" to "REQUIRED"),
    )

    @Test fun `malformed currency is reported by publish validation`() = assertInvalid(
        readyCourse().copy(priceDisplay = PriceDisplay(100, "")), mapOf("priceDisplay" to "REQUIRED"),
    )

    @Test fun `missing thumbnail is reported by publish validation`() = assertInvalid(
        readyCourse().copy(thumbnailMediaId = null), mapOf("thumbnail" to "REQUIRED"),
    )

    @Test fun `empty curriculum is reported by publish validation`() = assertInvalid(
        readyCourse().copy(sections = emptyList()), mapOf("curriculum" to "NO_SECTIONS"),
    )

    @Test fun `section without lessons is reported by publish validation`() {
        val section = Section("empty-section", "Empty", 0)
        assertInvalid(
            readyCourse().copy(sections = listOf(section)),
            mapOf("section.empty-section.lessons" to "NO_LESSONS"),
        )
    }

    @Test fun `lesson without video is reported by publish validation`() {
        val lesson = readyLesson().copy(lessonId = "missing-video", videoMediaId = null)
        assertInvalid(
            readyCourse().copy(sections = listOf(Section("section", "Section", 0, listOf(lesson)))),
            mapOf("lesson.missing-video.videoMediaId" to "REQUIRED"),
        )
    }

    @Test
    fun `ready course publishes successfully`() = runBlocking {
        val course = readyCourse()
        coEvery { repository.findById(course.id!!) } returns course
        coEvery { repository.replace(any()) } answers { firstArg<CourseDocument>() }

        val response = service.publish(principal, course.id!!.toHexString())

        assertEquals("published", response.status)
    }

    private fun assertInvalid(course: CourseDocument, expectedFields: Map<String, String>) = runBlocking {
        coEvery { repository.findById(course.id!!) } returns course

        val error = assertFailsWith<ApiException.Validation> {
            service.publish(principal, course.id!!.toHexString())
        }

        assertEquals(expectedFields, error.fields)
    }

    private fun readyCourse() = CourseDocument(
        id = ObjectId(), instructorId = instructorId, title = "Ready course", description = "Description",
        categoryId = ObjectId(), level = "beginner", contentLanguage = "en",
        priceDisplay = PriceDisplay(100, "USD"), thumbnailMediaId = ObjectId(), status = "draft",
        ratingSeed = 4.5, sections = listOf(Section("section", "Section", 0, listOf(readyLesson()))),
        createdAt = Instant.fromEpochMilliseconds(1), updatedAt = Instant.fromEpochMilliseconds(1),
    )

    private fun readyLesson() = Lesson("lesson", "Lesson", "Description", 0, ObjectId())
}
