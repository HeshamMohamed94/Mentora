package com.mentora.backend.certificates.service

import com.mentora.backend.certificates.repository.CertificateDocument
import com.mentora.backend.certificates.repository.CertificateRepository
import com.mentora.backend.common.ApiException
import com.mentora.backend.common.MentoraPrincipal
import com.mentora.backend.common.Page
import com.mentora.backend.common.PageRequest
import com.mentora.backend.common.toPage
import com.mentora.backend.common.withRetryableTransaction
import com.mentora.backend.courses.service.CourseService
import com.mentora.backend.progress.service.ProgressService
import com.mentora.backend.quiz.service.QuizService
import com.mentora.backend.users.service.UserService
import com.mongodb.ErrorCategory
import com.mongodb.MongoWriteException
import com.mongodb.kotlin.client.coroutine.MongoClient
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable data class CertificateSummaryResponse(
    val id: String,
    val courseTitleSnapshot: String,
    val instructorNameSnapshot: String,
    val issuedAt: Instant,
)

@Serializable data class CertificateDetailResponse(
    val id: String,
    val studentNameSnapshot: String,
    val courseTitleSnapshot: String,
    val instructorNameSnapshot: String,
    val completionDateSnapshot: Instant,
    val issuedAt: Instant,
)

class CertificateService(
    private val repository: CertificateRepository,
    private val progress: ProgressService,
    private val quiz: QuizService,
    private val courses: CourseService,
    private val users: UserService,
    private val mongoClient: MongoClient,
) {
    suspend fun checkAndIssueIfComplete(principal: MentoraPrincipal, courseId: String) {
        val course = courses.get(courseId, principal)
        val objectCourseId = objectId(courseId)
        val snapshot = progress.snapshotForCompletion(objectCourseId, principal.userId)
        if (snapshot.courseCompletedAt != null) return

        val totalLessons = course.sections.sumOf { it.lessons.size }
        val hasQuiz = quiz.hasQuiz(objectCourseId)
        if (snapshot.completedLessonCount < totalLessons || (hasQuiz && snapshot.quizPassed != true)) return

        val instructor = users.getProfile(ObjectId(course.instructorId))
        val student = users.getProfile(principal.userId)
        val now = Clock.System.now()
        try {
            mongoClient.withRetryableTransaction { session ->
                progress.markCourseCompleted(session, principal.userId, objectCourseId, now)
                repository.insert(session, CertificateDocument(
                    userId = principal.userId,
                    courseId = objectCourseId,
                    issuedAt = now,
                    studentNameSnapshot = student.name,
                    courseTitleSnapshot = course.title,
                    instructorNameSnapshot = instructor.name,
                    completionDateSnapshot = now,
                ))
            }
        } catch (error: MongoWriteException) {
            if (error.error.category != ErrorCategory.DUPLICATE_KEY) throw error
            // A certificate for this (user, course) pair already exists, so the certificate insert
            // above failed its unique-index check and the whole transaction — including this
            // attempt's `markCourseCompleted` — was rolled back together with it. This is reachable
            // without any client misbehavior: SeedData.kt's `ensureCurriculum` clears `progress`
            // rows when a course's curriculum is rebuilt but deliberately leaves `certificates`
            // untouched (by design — a certificate must stay a permanent record even if the
            // curriculum it was earned against changes shape), so a student who already held a
            // certificate can naturally re-walk the (new) lessons/quiz and land back here. Without
            // this recovery, `courseCompletedAt` would be stuck unset forever even though the
            // student has, in every real sense, completed the course again — the exact Phase 8 A4
            // "re-passing an already-passed quiz doesn't route to the completion screen" defect.
            // Safe to backfill standalone: idempotent, and no certificate write is attempted here.
            // `withRetryableTransaction` checks for a `TransientTransactionError` label BEFORE any
            // per-call-site exception type narrowing, so by the time a MongoWriteException reaches
            // here it is genuinely a non-transient DUPLICATE_KEY, not a write-conflict retry candidate.
            progress.markCourseCompleted(principal.userId, objectCourseId, now)
        }
    }

    suspend fun list(principal: MentoraPrincipal, page: PageRequest): Page<CertificateSummaryResponse> =
        repository.list(principal.userId, page).toPage(page.limit) { requireNotNull(it.id) }.let { result ->
            Page(result.items.map { it.toSummary() }, result.nextCursor)
        }

    suspend fun get(id: String, principal: MentoraPrincipal): CertificateDetailResponse {
        val certificate = repository.findByIdAndUser(parsePublicId(id), principal.userId)
            ?: throw certificateNotFound()
        return certificate.toDetail()
    }

    private fun objectId(value: String) = try { ObjectId(value) }
    catch (_: IllegalArgumentException) { throw ApiException.Validation(fields = mapOf("id" to "INVALID")) }

    private fun parsePublicId(value: String): ObjectId {
        val match = PUBLIC_ID.matchEntire(value) ?: throw certificateNotFound()
        return try { ObjectId(match.groupValues.drop(1).joinToString("").lowercase()) }
        catch (_: IllegalArgumentException) { throw certificateNotFound() }
    }

    private fun publicId(id: ObjectId): String = id.toHexString().uppercase().chunked(4).joinToString("-", "MTR-")
    private fun certificateNotFound() = ApiException.NotFound("CERTIFICATE_NOT_FOUND", "The certificate was not found.")
    private fun CertificateDocument.toSummary() = CertificateSummaryResponse(
        publicId(requireNotNull(id)), courseTitleSnapshot, instructorNameSnapshot, issuedAt,
    )
    private fun CertificateDocument.toDetail() = CertificateDetailResponse(
        publicId(requireNotNull(id)), studentNameSnapshot, courseTitleSnapshot,
        instructorNameSnapshot, completionDateSnapshot, issuedAt,
    )

    private companion object {
        val PUBLIC_ID = Regex("^MTR-([0-9A-Fa-f]{4})-([0-9A-Fa-f]{4})-([0-9A-Fa-f]{4})-([0-9A-Fa-f]{4})-([0-9A-Fa-f]{4})-([0-9A-Fa-f]{4})$")
    }
}
