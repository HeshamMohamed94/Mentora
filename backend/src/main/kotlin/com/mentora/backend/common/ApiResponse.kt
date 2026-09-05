package com.mentora.backend.common

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * The one response envelope shape for every route in the system — API_CONTRACT.md § 3.
 * Never construct a raw success/error body outside of these helpers.
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

typealias JsonMap = Map<String, JsonElement>
