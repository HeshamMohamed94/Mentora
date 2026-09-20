package com.mentora.backend.aitutor.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Shared `Json` instance for everything on the Anthropic wire — request encoding and SSE/error
 * payload decoding. `ignoreUnknownKeys` so unrecognized/future fields never crash parsing
 * (PHASE_6_SYSTEM_DESIGN.md § 3.4 / R4).
 */
internal val anthropicJson = Json { ignoreUnknownKeys = true }

/** Outbound request body — exactly five top-level keys, nothing more (Design § 3.3, C4). */
@Serializable
internal data class AnthropicRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<AnthropicMessage>,
    val stream: Boolean,
)

@Serializable
internal data class AnthropicMessage(val role: String, val content: String)

// ---- Inbound SSE payload shapes (Design § 3.3/§ 3.4) ----

@Serializable
internal data class AnthropicMessageStart(val message: AnthropicMessageStartBody)

@Serializable
internal data class AnthropicMessageStartBody(val usage: AnthropicInputUsage? = null)

@Serializable
internal data class AnthropicInputUsage(@SerialName("input_tokens") val inputTokens: Int? = null)

@Serializable
internal data class AnthropicContentBlockDelta(val index: Int? = null, val delta: AnthropicDelta)

@Serializable
internal data class AnthropicDelta(val type: String? = null, val text: String? = null)

@Serializable
internal data class AnthropicMessageDelta(
    val delta: AnthropicMessageDeltaBody? = null,
    val usage: AnthropicOutputUsage? = null,
)

@Serializable
internal data class AnthropicMessageDeltaBody(@SerialName("stop_reason") val stopReason: String? = null)

@Serializable
internal data class AnthropicOutputUsage(@SerialName("output_tokens") val outputTokens: Int? = null)

/** `{"type":"error","error":{"type":"<error_type>","message":"<msg>"}}` — arrives either as a
 * non-2xx HTTP response body or as an SSE `event: error` payload (Design § 3.3). */
@Serializable
internal data class AnthropicErrorEnvelope(val error: AnthropicErrorDetail)

@Serializable
internal data class AnthropicErrorDetail(val type: String, val message: String)
