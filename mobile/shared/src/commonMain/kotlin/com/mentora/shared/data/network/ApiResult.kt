package com.mentora.shared.data.network

/**
 * The only shape networking code is allowed to hand upward — a raw exception (or the wire
 * [ApiError]/[ApiErrorBody] envelope) must never escape to a repository/use-case caller.
 *
 * [Failure.code] is the contract: every caller branches on it. [Failure.message] is
 * diagnostic-only (logging/debugging) — it is never the primary signal for branching logic and
 * must never be assumed localized, stable across backend versions, or safe to show verbatim as
 * UI copy. UI-facing copy is derived from [Failure.code] by the platform layer, not from
 * [Failure.message].
 */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()

    data class Failure(
        val code: ApiErrorCode,
        val message: String,
        val fields: Map<String, String>?,
        val httpStatus: Int,
    ) : ApiResult<Nothing>()
}

/** Maps the wire error envelope to the shared [ApiResult.Failure] shape, resolving [ApiErrorBody.code]
 * to a typed [ApiErrorCode] (falling back to [ApiErrorCode.Unknown] for unrecognized codes). */
fun ApiErrorBody.toFailure(httpStatus: Int): ApiResult.Failure =
    ApiResult.Failure(
        code = ApiErrorCode.fromWire(code),
        message = message,
        fields = fields,
        httpStatus = httpStatus,
    )

/** Convenience overload for the full [ApiError] envelope (its [ApiError.meta] is not carried onto
 * [ApiResult.Failure] — only [ApiError.error] is contract-relevant to callers). */
fun ApiError.toFailure(httpStatus: Int): ApiResult.Failure = error.toFailure(httpStatus)
