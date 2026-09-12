package com.mentora.shared.data.repository.media

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.domain.model.PlaybackSource

/**
 * The only media network surface `domain/usecase/media` use cases are allowed to depend on — mirrors
 * [com.mentora.shared.data.repository.progress.ProgressRepository]'s "interface + Impl" pattern
 * (`execution/PHASE_3_KMP_PLAN.md` Task 13).
 *
 * Deliberately exposes ONLY [getPlaybackUrl] — no generic "fetch media by id" method exists here.
 * The public `GET /api/v1/media/{id}/file` route (thumbnails/avatars) needs no network round trip
 * from `shared` at all — it is a plain, unsigned static-file URL a platform image loader fetches
 * directly, so its resolution lives entirely in
 * `com.mentora.shared.domain.usecase.media.ResolveThumbnailUrlUseCase` as pure URL construction,
 * with no dependency on this repository. This asymmetry is intentional: it makes it structurally
 * impossible to route a lesson video through the thumbnail path via this repository, since no method
 * here accepts an arbitrary "any media id" and returns a `/file` url.
 */
interface MediaRepository {
    /**
     * `GET /api/v1/media/{mediaId}/playback-url`, auth required → [PlaybackSource] with a RELATIVE
     * [PlaybackSource.url] (resolution to absolute happens one layer up, in
     * `GetLessonPlaybackSourceUseCase`, never here). [mediaId] must be a `lessonVideo`-kind media id
     * — `MediaService.playbackUrl()` 404s (`MEDIA_NOT_FOUND`) for any other kind.
     *
     * A caller who is neither the course's owning instructor nor enrolled in it surfaces as an
     * ordinary [ApiResult.Failure] with
     * [com.mentora.shared.data.network.ApiErrorCode.ForbiddenNotEnrolled] — a typed, distinguishable
     * failure (`MediaService.playbackUrl()` → `EnrollmentService.requireEnrollment()` →
     * `ApiException.ForbiddenNotEnrolled`), never folded into a generic error.
     */
    suspend fun getPlaybackUrl(mediaId: String): ApiResult<PlaybackSource>
}
