package com.mentora.backend.learningpaths.service

import com.mentora.backend.common.ApiException
import com.mentora.backend.common.MentoraPrincipal
import com.mentora.backend.courses.service.CourseService
import com.mentora.backend.learningpaths.repository.LearningPathDocument
import com.mentora.backend.learningpaths.repository.LearningPathRepository
import com.mentora.backend.progress.service.ProgressService
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable data class LearningPathSummary(
    val id: String, val title: String, val description: String, val courseCount: Int,
)
@Serializable data class LearningPathCourse(
    val id: String, val title: String, val thumbnailMediaId: String?,
)
@Serializable data class LearningPathResponse(
    val id: String,
    val title: String,
    val description: String,
    val courses: List<LearningPathCourse>,
    val progressPercent: Int?,
    val isFollowing: Boolean,
)
@Serializable data class LearningPathFollowResponse(val isFollowing: Boolean)

class LearningPathService(
    private val repository: LearningPathRepository,
    private val courses: CourseService,
    private val progress: ProgressService,
) {
    suspend fun list(): List<LearningPathSummary> = repository.list().map { it.toSummary() }

    suspend fun get(id: String, principal: MentoraPrincipal?): LearningPathResponse {
        val path = findPath(id)
        val resolved = path.courseIds.mapNotNull { courseId ->
            try {
                courses.get(courseId.toHexString(), principal).let {
                    LearningPathCourse(it.id, it.title, it.thumbnailMediaId)
                }
            } catch (error: ApiException.NotFound) {
                if (error.code == "COURSE_NOT_FOUND") null else throw error
            }
        }
        val progressPercent = principal?.let {
            if (resolved.isEmpty()) 0 else resolved.count { course ->
                progress.snapshotForCompletion(ObjectId(course.id), principal.userId).courseCompletedAt != null
            } * 100 / resolved.size
        }
        return LearningPathResponse(
            requireNotNull(path.id).toHexString(), path.title, path.description, resolved,
            progressPercent,
            principal?.let { repository.isFollowing(it.userId, requireNotNull(path.id)) } ?: false,
        )
    }

    suspend fun follow(id: String, principal: MentoraPrincipal): LearningPathFollowResponse {
        val pathId = requireNotNull(findPath(id).id)
        repository.follow(principal.userId, pathId, Clock.System.now())
        return LearningPathFollowResponse(true)
    }

    suspend fun unfollow(id: String, principal: MentoraPrincipal): LearningPathFollowResponse {
        val pathId = requireNotNull(findPath(id).id)
        repository.unfollow(principal.userId, pathId)
        return LearningPathFollowResponse(false)
    }

    private suspend fun findPath(id: String): LearningPathDocument =
        repository.findById(objectId(id)) ?: throw notFound()

    private fun objectId(value: String) = try {
        ObjectId(value)
    } catch (_: IllegalArgumentException) {
        throw ApiException.Validation(fields = mapOf("id" to "INVALID"))
    }

    private fun notFound() = ApiException.NotFound("LEARNING_PATH_NOT_FOUND", "The learning path was not found.")
    private fun LearningPathDocument.toSummary() =
        LearningPathSummary(requireNotNull(id).toHexString(), title, description, courseIds.size)
}
