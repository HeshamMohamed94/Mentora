package com.mentora.backend.common

import io.ktor.server.application.ApplicationCall
import org.bson.types.ObjectId
import java.util.Base64

/**
 * Cursor-based pagination — API_CONTRACT.md § 5. The cursor is an opaque, base64-encoded ObjectId
 * of the last item on the previous page; collections are paginated by sorting on `_id` (or another
 * strictly-ordered field documented per-endpoint) and filtering for values after the cursor.
 */
data class PageRequest(
    val cursor: ObjectId?,
    val limit: Int,
) {
    companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 100

        fun fromCall(call: ApplicationCall): PageRequest {
            val rawLimit = call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_LIMIT
            val limit = rawLimit.coerceIn(1, MAX_LIMIT)
            val rawCursor = call.request.queryParameters["cursor"]
            val cursor = rawCursor?.let { decodeCursor(it) }
            return PageRequest(cursor, limit)
        }

        private fun decodeCursor(value: String): ObjectId = try {
            ObjectId(String(Base64.getUrlDecoder().decode(value)))
        } catch (e: Exception) {
            throw ApiException.Validation("Invalid pagination cursor.", fields = mapOf("cursor" to "INVALID"))
        }
    }
}

fun ObjectId.encodeCursor(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(this.toHexString().toByteArray())

data class Page<T>(
    val items: List<T>,
    val nextCursor: String?,
)

/** Fetches one page's worth + 1 extra item to determine whether a next page exists, per standard
 * cursor-pagination practice — trims the extra item before returning. */
fun <T> List<T>.toPage(limit: Int, cursorOf: (T) -> ObjectId): Page<T> {
    val hasMore = this.size > limit
    val items = if (hasMore) this.take(limit) else this
    val nextCursor = if (hasMore) cursorOf(items.last()).encodeCursor() else null
    return Page(items, nextCursor)
}
