package com.mentora.shared.domain.usecase.media

import com.mentora.shared.config.ApiEnvironment
import com.mentora.shared.config.resolveUrl
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.media.MediaRepository
import com.mentora.shared.domain.model.PlaybackSource

/**
 * `GET /api/v1/media/{mediaId}/playback-url` for a lesson's video, resolved to something a
 * [com.mentora.shared.playback.LessonPlaybackController] can consume directly.
 *
 * [mediaId] is expected to be a [com.mentora.shared.domain.model.Lesson.videoMediaId] value — the
 * ONLY intended source of this parameter in `shared`. The backend independently 404s
 * (`MEDIA_NOT_FOUND`) if it isn't a `lessonVideo`-kind id, so passing anything else fails cleanly
 * rather than silently.
 *
 * This is the single, central place [PlaybackSource.url] is turned from the wire's RELATIVE form
 * into an ABSOLUTE url (via [resolveUrl] against [environment]'s [ApiEnvironment.baseUrl]) — per
 * `execution/PHASE_3_KMP_PLAN.md` Task 13 AC #2, this resolution happens exactly once, here, and is
 * never re-implemented at another call site
 * ([com.mentora.shared.domain.usecase.media.RefreshPlaybackUrlUseCase] reuses this use case rather
 * than resolving again itself).
 */
class GetLessonPlaybackSourceUseCase(
    private val repository: MediaRepository,
    private val environment: ApiEnvironment,
) {
    suspend operator fun invoke(mediaId: String): ApiResult<PlaybackSource> =
        when (val result = repository.getPlaybackUrl(mediaId)) {
            is ApiResult.Success -> ApiResult.Success(result.data.copy(url = environment.resolveUrl(result.data.url)))
            is ApiResult.Failure -> result
        }
}
