package com.mentora.shared.data.network

import kotlinx.serialization.json.Json

/**
 * The single shared `Json` instance for the wire contract. Configuration is kept compatible with
 * the backend's `plugins/Serialization.kt` (`ignoreUnknownKeys = true`, `explicitNulls = false`),
 * plus `isLenient = false` so malformed/relaxed JSON is rejected rather than silently accepted.
 * All (de)serialization of [ApiSuccess]/[ApiError]/feature DTOs must go through this instance —
 * never a locally-configured `Json {}` block elsewhere in `shared`.
 */
val MentoraJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = false
}
