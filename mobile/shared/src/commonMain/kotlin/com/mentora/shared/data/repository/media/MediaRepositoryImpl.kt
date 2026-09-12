package com.mentora.shared.data.repository.media

import com.mentora.shared.data.network.ApiClient
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.dto.PlaybackUrlDto
import com.mentora.shared.domain.model.PlaybackSource

/** The real [MediaRepository]. No `?language=` on this endpoint — playback-url is not one of the 4
 * reads that carries it (`execution/PHASE_3_KMP_PLAN.md`'s D57/C3 list). */
class MediaRepositoryImpl(private val apiClient: ApiClient) : MediaRepository {

    override suspend fun getPlaybackUrl(mediaId: String): ApiResult<PlaybackSource> =
        when (val result = apiClient.get<PlaybackUrlDto>("/api/v1/media/$mediaId/playback-url")) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Failure -> result
        }

    private fun PlaybackUrlDto.toDomain(): PlaybackSource = PlaybackSource(url = url, expiresAt = expiresAt)
}
