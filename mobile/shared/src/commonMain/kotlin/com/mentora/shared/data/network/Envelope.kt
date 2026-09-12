package com.mentora.shared.data.network

import kotlinx.serialization.Serializable

/**
 * Wire-shape mirror of the backend's single response envelope — see
 * `backend/src/main/kotlin/com/mentora/backend/common/ApiResponse.kt`, which is the source of
 * truth these types must match byte-exactly. Never hand one of these to a repository caller
 * directly — [ApiResult] (built from these via `toFailure`/`toResult` mapping at the network edge)
 * is the only shape allowed to cross that boundary.
 *
 * `explicitNulls = false` on both sides means an omitted JSON key and an explicit `null` are
 * indistinguishable and both deserialize to Kotlin `null` for every optional field here.
 */
@Serializable
data class ApiMeta(
    val requestId: String,
    val nextCursor: String? = null,
)

@Serializable
data class ApiSuccess<T>(
    val data: T,
    val meta: ApiMeta,
)

@Serializable
data class ApiErrorBody(
    val code: String,
    val message: String,
    val fields: Map<String, String>? = null,
)

@Serializable
data class ApiError(
    val error: ApiErrorBody,
    val meta: ApiMeta,
)
