package com.mentora.shared.domain.usecase.media

import com.mentora.shared.config.ApiEnvironment
import com.mentora.shared.config.resolveUrl

/**
 * Builds the absolute url for the PUBLIC `GET /api/v1/media/{mediaId}/file` route — course
 * thumbnails and user avatars ONLY. Verified from source
 * (`MediaService.publicFile()`/`MediaRoutes.kt:57-59`): this route sits OUTSIDE
 * `authenticate("jwt-auth")` and needs no token of any kind — it is genuinely public/unsigned, unlike
 * the lesson-video `/stream` route. No network round trip is needed to obtain this url at all; it is
 * pure string construction, so this use case is not `suspend` and has no dependency on
 * [com.mentora.shared.data.repository.media.MediaRepository].
 *
 * **Never a valid path to a lesson video.** `MediaService.publicFile()` explicitly 404s
 * (`MEDIA_NOT_FOUND`) whenever the requested id's `kind == "lessonVideo"`
 * (`if (media.kind == LESSON_VIDEO) throw mediaNotFound()`) — verified from source, this is
 * deliberate backend behavior, not an accident `shared` needs to route around. On the `shared` side,
 * this use case is kept structurally separate from [GetLessonPlaybackSourceUseCase]/
 * [RefreshPlaybackUrlUseCase] (different function, different file, no shared call path) and its
 * parameter is named/typed [thumbnailMediaId] specifically to signal "this must come from a
 * thumbnail-context field" — e.g.
 * [com.mentora.shared.domain.model.CourseSummary.thumbnailMediaId]/
 * [com.mentora.shared.domain.model.Course.thumbnailMediaId] — never from
 * [com.mentora.shared.domain.model.Lesson.videoMediaId]. Even if a caller ignored that convention,
 * the server-side 404 above is the real, load-bearing guarantee a lesson video can never actually be
 * served through this route.
 */
class ResolveThumbnailUrlUseCase(private val environment: ApiEnvironment) {
    operator fun invoke(thumbnailMediaId: String): String =
        environment.resolveUrl("/api/v1/media/$thumbnailMediaId/file")
}
