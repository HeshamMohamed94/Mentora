package com.mentora.backend.media.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.mentora.backend.common.ApiException
import com.mentora.backend.common.MentoraPrincipal
import com.mentora.backend.common.Role
import com.mentora.backend.common.requireRole
import com.mentora.backend.config.AppConfig
import com.mentora.backend.courses.service.CourseService
import com.mentora.backend.enrollment.service.EnrollmentService
import com.mentora.backend.media.repository.MediaDocument
import com.mentora.backend.media.repository.MediaRepository
import com.mentora.backend.media.storage.MediaContent
import com.mentora.backend.media.storage.MediaStorage
import io.ktor.utils.io.ByteReadChannel
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import java.util.Date
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

data class MediaUpload(
    val kind: String?,
    val ownerRefId: String?,
    val contentType: String?,
    val courseId: String?,
    val durationSeconds: String?,
    val bytes: ByteReadChannel,
)

@Serializable data class MediaUploadResponse(val mediaId: String, val storageKey: String)
@Serializable data class PlaybackUrlResponse(val url: String, val expiresAt: Instant)

class MediaService(
    private val repository: MediaRepository,
    private val storage: MediaStorage,
    private val courses: CourseService,
    private val enrollment: EnrollmentService,
    appConfig: AppConfig,
) {
    private val signingAlgorithm = Algorithm.HMAC256(appConfig.jwtSigningSecret)
    private val tokenVerifier = JWT.require(signingAlgorithm).build()

    suspend fun upload(upload: MediaUpload, principal: MentoraPrincipal): MediaUploadResponse {
        val validated = validate(upload, principal)
        val storageKey = storageKey(validated)
        val stored = storage.store(storageKey, validated.contentType, upload.bytes)
        val document = repository.insert(
            MediaDocument(
                uploadedByUserId = principal.userId,
                kind = validated.kind,
                ownerRefId = validated.ownerRefId,
                courseId = validated.courseId,
                storageKey = stored.storageKey,
                contentType = validated.contentType,
                sizeBytes = stored.sizeBytes,
                durationSeconds = validated.durationSeconds,
                createdAt = Clock.System.now(),
            )
        )
        return MediaUploadResponse(requireNotNull(document.id).toHexString(), document.storageKey)
    }

    suspend fun publicFile(mediaId: String): MediaContent {
        val media = findMedia(mediaId)
        if (media.kind == LESSON_VIDEO) throw mediaNotFound()
        return storage.read(media.storageKey)
    }

    suspend fun playbackUrl(mediaId: String, principal: MentoraPrincipal): PlaybackUrlResponse {
        val media = findMedia(mediaId)
        if (media.kind != LESSON_VIDEO) throw mediaNotFound()
        val courseId = requireNotNull(media.courseId)
        if (principal.role != Role.admin) {
            try {
                courses.requireOwnership(courseId.toHexString(), principal)
            } catch (error: ApiException.ForbiddenNotOwner) {
                enrollment.requireEnrollment(principal.userId, courseId)
            }
        }
        val expiresAt = Clock.System.now() + PLAYBACK_TTL_MINUTES.minutes
        val token = JWT.create()
            .withClaim("mediaId", mediaId)
            .withClaim("purpose", PLAYBACK_PURPOSE)
            .withExpiresAt(Date.from(java.time.Instant.parse(expiresAt.toString())))
            .sign(signingAlgorithm)
        return PlaybackUrlResponse("/api/v1/media/$mediaId/stream?token=$token", expiresAt)
    }

    suspend fun stream(mediaId: String): MediaContent {
        val media = findMedia(mediaId)
        if (media.kind != LESSON_VIDEO) throw mediaNotFound()
        return storage.read(media.storageKey)
    }

    fun verifyPlaybackToken(mediaId: String, token: String?) {
        try {
            val decoded = tokenVerifier.verify(token ?: throw ApiException.TokenInvalid())
            if (decoded.getClaim("purpose").asString() != PLAYBACK_PURPOSE ||
                decoded.getClaim("mediaId").asString() != mediaId
            ) throw ApiException.TokenInvalid()
        } catch (error: JWTVerificationException) {
            throw ApiException.TokenInvalid()
        }
    }

    private suspend fun validate(upload: MediaUpload, principal: MentoraPrincipal): ValidatedUpload {
        val kind = upload.kind?.takeIf { it in SUPPORTED_TYPES }
            ?: throw ApiException.Validation(fields = mapOf("kind" to "UNSUPPORTED"))
        val ownerRefId = ownerRefId(kind, upload.ownerRefId)
        val contentType = upload.contentType?.takeIf { it in requireNotNull(SUPPORTED_TYPES[kind]) }
            ?: throw ApiException.Validation(fields = mapOf("contentType" to "UNSUPPORTED"))
        val courseId = authorize(kind, ownerRefId, upload.courseId, principal)
        val duration = upload.durationSeconds?.toIntOrNull()?.takeIf { it >= 0 }
        return ValidatedUpload(kind, ownerRefId, courseId, contentType, duration)
    }

    private suspend fun authorize(
        kind: String,
        ownerRefId: String,
        rawCourseId: String?,
        principal: MentoraPrincipal,
    ): ObjectId? = when (kind) {
        AVATAR -> {
            if (ObjectId(ownerRefId) != principal.userId) throw ApiException.ForbiddenNotOwner()
            null
        }
        COURSE_THUMBNAIL -> {
            principal.requireRole(Role.instructor)
            courses.requireOwnership(ownerRefId, principal)
            null
        }
        else -> authorizeLessonVideo(ownerRefId, rawCourseId, principal)
    }

    private suspend fun authorizeLessonVideo(
        lessonId: String,
        rawCourseId: String?,
        principal: MentoraPrincipal,
    ): ObjectId {
        principal.requireRole(Role.instructor)
        if (rawCourseId == null) throw ApiException.Validation(fields = mapOf("courseId" to "REQUIRED"))
        val courseId = objectId(rawCourseId, "courseId")
        val course = courses.requireOwnership(courseId.toHexString(), principal)
        if (course.sections.flatMap { it.lessons }.none { it.lessonId == lessonId }) {
            throw ApiException.NotFound("LESSON_NOT_FOUND", "The lesson was not found.")
        }
        return courseId
    }

    private suspend fun findMedia(mediaId: String): MediaDocument {
        val id = objectId(mediaId, "mediaId")
        return repository.findById(id) ?: throw mediaNotFound()
    }

    private fun objectId(raw: String?, field: String): ObjectId = try {
        ObjectId(raw ?: throw IllegalArgumentException())
    } catch (error: IllegalArgumentException) {
        throw ApiException.Validation(fields = mapOf(field to "INVALID"))
    }

    private fun ownerRefId(kind: String, raw: String?): String {
        if (kind == LESSON_VIDEO) {
            return raw?.takeIf { runCatching { UUID.fromString(it) }.isSuccess }
                ?: throw ApiException.Validation(fields = mapOf("ownerRefId" to "INVALID"))
        }
        return objectId(raw, "ownerRefId").toHexString()
    }

    private fun storageKey(upload: ValidatedUpload): String {
        val extension = requireNotNull(EXTENSIONS[upload.contentType])
        return "${upload.kind}/${upload.ownerRefId}/${UUID.randomUUID()}.$extension"
    }

    private fun mediaNotFound() = ApiException.NotFound("MEDIA_NOT_FOUND", "The media was not found.")

    private data class ValidatedUpload(
        val kind: String,
        val ownerRefId: String,
        val courseId: ObjectId?,
        val contentType: String,
        val durationSeconds: Int?,
    )

    companion object {
        private const val AVATAR = "avatar"
        private const val COURSE_THUMBNAIL = "courseThumbnail"
        private const val LESSON_VIDEO = "lessonVideo"
        private const val PLAYBACK_PURPOSE = "media-playback"
        private const val PLAYBACK_TTL_MINUTES = 5
        private val SUPPORTED_TYPES = mapOf(
            COURSE_THUMBNAIL to setOf("image/jpeg", "image/png", "image/webp"),
            AVATAR to setOf("image/jpeg", "image/png", "image/webp"),
            LESSON_VIDEO to setOf("video/mp4", "video/webm"),
        )
        private val EXTENSIONS = mapOf(
            "image/jpeg" to "jpg", "image/png" to "png", "image/webp" to "webp",
            "video/mp4" to "mp4", "video/webm" to "webm",
        )
    }
}
