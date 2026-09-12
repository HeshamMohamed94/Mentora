package com.mentora.shared.data.network.dto

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * `GET /api/v1/media/{mediaId}/playback-url`'s response — mirrors `MediaService.kt:36`'s
 * `PlaybackUrlResponse` exactly: `url, expiresAt`, verified from source. [url] is relative on the
 * wire (`MediaService.playbackUrl()` builds `/api/v1/media/$mediaId/stream?token=$token`) — this DTO
 * carries it unresolved; see [com.mentora.shared.domain.model.PlaybackSource]'s kdoc for where
 * resolution to an absolute URL happens.
 */
@Serializable
data class PlaybackUrlDto(
    val url: String,
    val expiresAt: Instant,
)
