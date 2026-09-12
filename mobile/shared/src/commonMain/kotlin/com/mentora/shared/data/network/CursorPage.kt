package com.mentora.shared.data.network

/**
 * Cursor-paginated result — mirrors `backend/src/main/kotlin/com/mentora/backend/common/Pagination.kt`'s
 * `Page<T>`. On the wire this is never its own JSON object: a paginated list response is an
 * [ApiSuccess] whose `data` is the plain item array and whose `meta.nextCursor` carries the
 * cursor (see `common/Responses.kt`'s `respondPage`). Task 3's `ApiClient` assembles a
 * [CursorPage] from those two pieces.
 *
 * [nextCursor] is an opaque, base64-encoded string token — it is never parsed, decoded, or
 * constructed client-side, only round-tripped back to the server as the next page's `?cursor=`.
 */
data class CursorPage<T>(
    val items: List<T>,
    val nextCursor: String?,
)
