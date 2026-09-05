package com.mentora.backend.enrollment.service

import com.mentora.backend.common.ApiException
import com.mentora.backend.common.MentoraPrincipal
import com.mentora.backend.common.Page
import com.mentora.backend.common.PageRequest
import com.mentora.backend.common.toPage
import com.mentora.backend.courses.service.CourseResponse
import com.mentora.backend.courses.service.CourseService
import com.mentora.backend.courses.service.PriceDisplayDto
import com.mentora.backend.enrollment.repository.DemoPurchaseDocument
import com.mentora.backend.enrollment.repository.EnrollmentDocument
import com.mentora.backend.enrollment.repository.EnrollmentRepository
import com.mentora.backend.users.service.UserService
import com.mongodb.ErrorCategory
import com.mongodb.MongoWriteException
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoClient
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable data class CheckoutCourse(val id: String, val title: String, val thumbnailMediaId: String?)
@Serializable data class CheckoutPreview(
    val course: CheckoutCourse,
    val instructorName: String,
    val priceDisplay: PriceDisplayDto,
)
@Serializable data class EnrollmentResponse(
    val id: String,
    val courseId: String,
    val source: String,
    val enrolledAt: Instant,
    val status: String,
)
@Serializable data class EnrollmentCompletion(val enrollment: EnrollmentResponse, val alreadyEnrolled: Boolean)

data class CompletionOutcome(val response: EnrollmentCompletion, val created: Boolean)

class EnrollmentService(
    private val repository: EnrollmentRepository,
    private val courses: CourseService,
    private val users: UserService,
    private val mongoClient: MongoClient,
) {
    suspend fun requireEnrollment(userId: ObjectId, courseId: ObjectId) {
        if (repository.find(userId, courseId) == null) throw ApiException.ForbiddenNotEnrolled()
    }

    suspend fun preview(courseId: String, principal: MentoraPrincipal): CheckoutPreview {
        val course = publishedCourse(courseId, principal)
        val instructor = users.getProfile(ObjectId(course.instructorId))
        return CheckoutPreview(
            CheckoutCourse(course.id, course.title, course.thumbnailMediaId),
            instructor.name,
            course.priceDisplay,
        )
    }

    suspend fun complete(courseId: String, principal: MentoraPrincipal): CompletionOutcome {
        val objectCourseId = objectId(courseId)
        try {
            mongoClient.startSession().use { session ->
                return inTransaction(session) {
                    repository.find(session, principal.userId, objectCourseId)?.let {
                        return@inTransaction CompletionOutcome(EnrollmentCompletion(it.toResponse(), true), false)
                    }
                    val course = publishedCourse(courseId, principal)
                    createEnrollment(session, principal.userId, objectCourseId, course.priceDisplay)
                }
            }
        } catch (error: MongoWriteException) {
            if (error.error.category != ErrorCategory.DUPLICATE_KEY) throw error
            val existing = repository.find(principal.userId, objectCourseId) ?: throw error
            return CompletionOutcome(EnrollmentCompletion(existing.toResponse(), true), false)
        }
    }

    suspend fun list(principal: MentoraPrincipal, page: PageRequest): Page<EnrollmentResponse> =
        repository.list(principal.userId, page).toPage(page.limit) { requireNotNull(it.id) }.let { pageSlice ->
            Page(pageSlice.items.map { it.toResponse() }, pageSlice.nextCursor)
        }

    private suspend fun createEnrollment(
        session: ClientSession,
        userId: ObjectId,
        courseId: ObjectId,
        priceDisplay: PriceDisplayDto,
    ): CompletionOutcome {
        val now = Clock.System.now()
        val demoPurchase = repository.insertDemoPurchase(session, DemoPurchaseDocument(
            userId = userId,
            courseId = courseId,
            priceDisplaySnapshot = priceDisplay,
            completedAt = now,
        ))
        val enrollment = repository.insertEnrollment(session, EnrollmentDocument(
            userId = userId,
            courseId = courseId,
            source = "demoCheckout",
            demoPurchaseId = requireNotNull(demoPurchase.id),
            enrolledAt = now,
            status = "active",
        ))
        return CompletionOutcome(EnrollmentCompletion(enrollment.toResponse(), false), true)
    }

    private suspend fun publishedCourse(courseId: String, principal: MentoraPrincipal): CourseResponse =
        courses.get(courseId, principal).also {
            if (it.status != "published") throw ApiException.NotFound("COURSE_NOT_FOUND", "The course was not found.")
        }

    private suspend fun <T> inTransaction(session: ClientSession, block: suspend () -> T): T {
        session.startTransaction()
        return try {
            block().also { session.commitTransaction() }
        } catch (error: Throwable) {
            if (session.hasActiveTransaction()) session.abortTransaction()
            throw error
        }
    }

    private fun objectId(value: String) = try {
        ObjectId(value)
    } catch (_: IllegalArgumentException) {
        throw ApiException.Validation(fields = mapOf("id" to "INVALID"))
    }

    private fun EnrollmentDocument.toResponse() = EnrollmentResponse(
        requireNotNull(id).toHexString(), courseId.toHexString(), source, enrolledAt, status,
    )
}
