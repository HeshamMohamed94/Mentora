package com.mentora.shared.domain.model

import kotlinx.datetime.Instant

/**
 * `GET /api/v1/media/{mediaId}/playback-url` (auth required) — mirrors the backend's
 * `PlaybackUrlResponse` (`MediaService.kt:36`) field-for-field: `url, expiresAt`, verified from
 * source.
 *
 * [url] is a short-lived, single-purpose stream URL of the shape
 * `/api/v1/media/{mediaId}/stream?token={jwt}` (`MediaService.playbackUrl()`) — the embedded
 * `?token=` query parameter, not an `Authorization` header, IS the auth mechanism the
 * `/media/{id}/stream` route checks (`MediaService.verifyPlaybackToken()`/`MediaRoutes.kt`'s
 * `/stream` handler sits OUTSIDE the `authenticate("jwt-auth")` block). A [LessonPlaybackController]
 * implementation (Phase 4/5) must hand this URL to the platform player AS-IS and attach no
 * `Authorization` header of its own. The server supports HTTP range requests on this route for
 * scrubbing — server-side behavior `shared` does not implement or need to know how to drive, just
 * be aware doesn't require any client-side range-request code here.
 *
 * On the wire, [url] is RELATIVE (`MediaService.playbackUrl()` returns a bare `/api/v1/...` path,
 * never an absolute URL). [com.mentora.shared.domain.model.PlaybackSource] instances obtained
 * directly from `MediaRepository` still carry this relative form — resolution to an absolute URL
 * against [com.mentora.shared.config.ApiEnvironment.baseUrl] happens exactly once, centrally, in
 * `com.mentora.shared.domain.usecase.media.GetLessonPlaybackSourceUseCase` (via
 * `com.mentora.shared.config.resolveUrl`) — never re-implemented at another call site. By the time a
 * caller outside `data.repository.media` sees a [PlaybackSource], [url] is always already absolute.
 *
 * [expiresAt] is the real, server-issued token expiry — a fresh token is valid for exactly 5 minutes
 * (`MediaService.PLAYBACK_TTL_MINUTES = 5`), verified from source, not assumed from docs. Kept as a
 * genuine [Instant] (unlike the raw ISO-8601 `String` convention `shared` otherwise uses for
 * display-only timestamps — see [User.createdAt]'s kdoc) because
 * `com.mentora.shared.domain.usecase.media.RefreshPlaybackUrlUseCase` needs to do real
 * near/past-expiry arithmetic against it, not just display it.
 *
 * Deliberately has **no duration field** — no response DTO anywhere in the backend carries lesson/
 * media duration (`MediaDocument.durationSeconds` exists but is never surfaced on any read path).
 * Duration comes from the platform player at runtime for the lesson currently loaded, never from
 * this type (`execution/PHASE_3_KMP_PLAN.md` Task 13 AC #7 / Decision C4). Do not add one.
 */
data class PlaybackSource(
    val url: String,
    val expiresAt: Instant,
)
