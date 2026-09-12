package com.mentora.shared.domain.usecase.media

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.media.MediaRepository
import com.mentora.shared.domain.model.PlaybackSource

/** A minimal [MediaRepository] test double — always returns [result], recording the last
 * [mediaId] it was asked for so a test can assert the right id was threaded through. */
internal class FakeMediaRepository(private val result: ApiResult<PlaybackSource>) : MediaRepository {
    var lastRequestedMediaId: String? = null
        private set

    override suspend fun getPlaybackUrl(mediaId: String): ApiResult<PlaybackSource> {
        lastRequestedMediaId = mediaId
        return result
    }
}
