package com.mentora.backend.progress.service

import com.mentora.backend.common.ApiException
import com.mentora.backend.common.MentoraPrincipal
import com.mentora.backend.courses.service.CourseResponse
import com.mentora.backend.courses.service.CourseService
import com.mentora.backend.enrollment.service.EnrollmentService
import com.mentora.backend.progress.repository.ProgressDocument
import com.mentora.backend.progress.repository.ProgressRepository
import com.mentora.backend.progress.repository.CompletionUpdate
import com.mentora.backend.progress.repository.PositionUpdate
import com.mongodb.kotlin.client.coroutine.ClientSession
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable data class PositionRequest(val positionSeconds: Int)
@Serializable data class ProgressResponse(
    val courseId: String,
    val completedLessonIds: List<String>,
    val currentLessonId: String?,
    val currentPositionSeconds: Int?,
    val quizPassed: Boolean?,
    val completionPercent: Int,
    val courseCompletedAt: Instant?,
)

data class ProgressCompletionSnapshot(
    val completedLessonCount: Int,
    val quizPassed: Boolean?,
    val courseCompletedAt: Instant?,
)

class ProgressService(
    private val repository: ProgressRepository,
    private val courses: CourseService,
    private val enrollments: EnrollmentService,
) {
    suspend fun snapshotForCompletion(courseId: ObjectId, userId: ObjectId): ProgressCompletionSnapshot {
        val progress = repository.find(userId, courseId)
        return ProgressCompletionSnapshot(
            completedLessonCount = progress?.completedLessonIds?.size ?: 0,
            quizPassed = progress?.quizPassed,
            courseCompletedAt = progress?.courseCompletedAt,
        )
    }

    suspend fun markCourseCompleted(
        session: ClientSession, userId: ObjectId, courseId: ObjectId, now: Instant,
    ) = repository.markCourseCompleted(session, userId, courseId, now)

    suspend fun setQuizPassed(
        session: ClientSession, userId: ObjectId, courseId: ObjectId, passed: Boolean, now: Instant,
    ) = repository.updateQuizPassed(session, userId, courseId, passed, now)

    suspend fun get(courseId: String, principal: MentoraPrincipal): ProgressResponse {
        val objectCourseId = objectId(courseId)
        enrollments.requireEnrollment(principal.userId, objectCourseId)
        return repository.findOrCreate(principal.userId, objectCourseId, Clock.System.now()).toResponse()
    }

    suspend fun complete(courseId: String, lessonId: String, principal: MentoraPrincipal): ProgressResponse {
        val objectCourseId = objectId(courseId)
        enrollments.requireEnrollment(principal.userId, objectCourseId)
        val course = requireLesson(courseId, lessonId, principal)
        val now = Clock.System.now()
        val current = repository.findOrCreate(principal.userId, objectCourseId, now)
        val completed = if (lessonId in current.completedLessonIds) current.completedLessonIds
            else current.completedLessonIds + (lessonId to now)
        val total = course.sections.sumOf { it.lessons.size }
        val percentage = if (total == 0) 0 else completed.size * 100 / total
        return repository.markComplete(CompletionUpdate(
            principal.userId, objectCourseId, lessonId, completed, percentage, now,
        )).toResponse()
    }

    suspend fun updatePosition(
        courseId: String, lessonId: String, request: PositionRequest, principal: MentoraPrincipal,
    ): ProgressResponse {
        val objectCourseId = objectId(courseId)
        enrollments.requireEnrollment(principal.userId, objectCourseId)
        if (request.positionSeconds < 0) {
            throw ApiException.Validation(fields = mapOf("positionSeconds" to "MUST_BE_NON_NEGATIVE"))
        }
        requireLesson(courseId, lessonId, principal)
        return repository.updatePosition(PositionUpdate(
            principal.userId, objectCourseId, lessonId, request.positionSeconds, Clock.System.now(),
        )).toResponse()
    }

    private suspend fun requireLesson(
        courseId: String, lessonId: String, principal: MentoraPrincipal,
    ): CourseResponse = courses.get(courseId, principal).also { course ->
        if (course.sections.none { section -> section.lessons.any { it.lessonId == lessonId } }) {
            throw ApiException.NotFound("LESSON_NOT_FOUND", "The lesson was not found.")
        }
    }

    private fun objectId(value: String) = try {
        ObjectId(value)
    } catch (_: IllegalArgumentException) {
        throw ApiException.Validation(fields = mapOf("id" to "INVALID"))
    }

    private fun ProgressDocument.toResponse() = ProgressResponse(
        courseId.toHexString(), completedLessonIds.keys.toList(), currentLessonId,
        currentPositionSeconds, quizPassed, completionPercent, courseCompletedAt,
    )
}
