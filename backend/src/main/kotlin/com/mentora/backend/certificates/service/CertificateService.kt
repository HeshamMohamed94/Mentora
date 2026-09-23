package com.mentora.backend.certificates.service

import com.mentora.backend.certificates.repository.CertificateDocument
import com.mentora.backend.certificates.repository.CertificateRepository
import com.mentora.backend.common.ApiException
import com.mentora.backend.common.MentoraPrincipal
import com.mentora.backend.common.Page
import com.mentora.backend.common.PageRequest
import com.mentora.backend.common.toPage
import com.mentora.backend.courses.service.CourseService
import com.mentora.backend.progress.service.ProgressService
import com.mentora.backend.quiz.service.QuizService
import com.mentora.backend.users.service.UserService
import com.mongodb.ErrorCategory
import com.mongodb.MongoException
import com.mongodb.MongoWriteException
import com.mongodb.kotlin.client.coroutine.ClientSession
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
        repeat(TRANSIENT_TRANSACTION_RETRY_LIMIT) { attempt ->
            try {
                mongoClient.startSession().use { session ->
                    inTransaction(session) {
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
                }
                return
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
                progress.markCourseCompleted(principal.userId, objectCourseId, now)
                return
            } catch (error: MongoException) {
                // Found via Phase 8 C4's gap analysis (same defect class as EnrollmentService's
                // `complete`, confirmed live by a concurrency test there): two truly concurrent
                // completions of the same (user, course) don't always fail the unique-index check
                // above as a clean MongoWriteException/DUPLICATE_KEY -- MongoDB's transaction
                // concurrency control can instead abort the losing transaction immediately with a
                // MongoCommandException (error 112, WriteConflict) carrying the driver's
                // "TransientTransactionError" label, which the DUPLICATE_KEY branch above never
                // catches, so it previously reached the caller as an unhandled 500. Retrying the
                // whole method (the pattern MongoDB's own docs prescribe for this label) resolves
                // it: `snapshotForCompletion`'s courseCompletedAt/DUPLICATE_KEY checks above will
                // see the winner's now-committed state and this call returns cleanly as a no-op.
                if (!error.hasErrorLabel("TransientTransactionError") || attempt == TRANSIENT_TRANSACTION_RETRY_LIMIT - 1) throw error
                // Fall through to the next `repeat` attempt: by then the winning transaction has
                // either committed (this attempt's insert will cleanly hit the DUPLICATE_KEY branch
                // above) or is still racing (this attempt hits WriteConflict again and retries once
                // more) -- no separate re-check needed, the loop body already handles both outcomes.
            }
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

    private suspend fun <T> inTransaction(session: ClientSession, block: suspend () -> T): T {
        session.startTransaction()
        return try { block().also { session.commitTransaction() } }
        catch (error: Throwable) {
            if (session.hasActiveTransaction()) session.abortTransaction()
            throw error
        }
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
        const val TRANSIENT_TRANSACTION_RETRY_LIMIT = 10
    }
}
