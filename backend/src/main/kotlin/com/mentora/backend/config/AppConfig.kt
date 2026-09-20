package com.mentora.backend.config

import java.io.File

/**
 * Typed application configuration, loaded eagerly at startup from environment variables
 * (with an optional local `.env` file as a fallback source — see [DotEnv]).
 *
 * Fails fast: a missing required value throws at construction time, before any request
 * can be served, per BACKEND_ARCHITECTURE.md § 6. Never logged in full (see [redactedSummary]).
 */
data class AppConfig(
    val mongoUri: String,
    val mongoDatabaseName: String,
    val jwtSigningSecret: String,
    val jwtIssuer: String,
    val accessTokenTtlMinutes: Long,
    val refreshTokenTtlDays: Long,
    val mediaStorageRoot: String,
    val corsAllowedOrigins: List<String>,
    val aiProviderApiKey: String?,
    val aiProviderModel: String,
    val logLevel: String,
    val aiTutorMessagesPerMinute: Int = 20,
    val aiTutorMessagesPerDay: Int = 200,
    val aiProviderMaxResponseTokens: Int = 1024,
    val aiProviderTimeoutSeconds: Int = 120,
) {
    companion object {
        fun load(): AppConfig {
            val env = DotEnv.load()

            fun required(key: String): String =
                env[key]?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException(
                        "Missing required configuration value: $key. " +
                            "Set it as an environment variable or in a local .env file (see .env.example)."
                    )

            fun optional(key: String, default: String): String = env[key]?.takeIf { it.isNotBlank() } ?: default

            fun rangedInt(key: String, default: Int, range: IntRange): Int {
                val value = optional(key, default.toString()).toInt()
                if (value !in range) {
                    throw IllegalStateException(
                        "Invalid configuration value for $key: $value. Must be in $range."
                    )
                }
                return value
            }

            return AppConfig(
                mongoUri = optional("MONGODB_URI", "mongodb://localhost:27017/mentora?replicaSet=rs0"),
                mongoDatabaseName = optional("MONGODB_DATABASE", "mentora"),
                jwtSigningSecret = required("JWT_SIGNING_SECRET"),
                jwtIssuer = optional("JWT_ISSUER", "mentora-backend"),
                accessTokenTtlMinutes = optional("ACCESS_TOKEN_TTL_MINUTES", "15").toLong(),
                refreshTokenTtlDays = optional("REFRESH_TOKEN_TTL_DAYS", "30").toLong(),
                mediaStorageRoot = optional("MEDIA_STORAGE_ROOT", "storage/media"),
                corsAllowedOrigins = optional("CORS_ALLOWED_ORIGINS", "http://localhost:3000")
                    .split(",").map { it.trim() }.filter { it.isNotEmpty() },
                aiProviderApiKey = env["AI_PROVIDER_API_KEY"]?.takeIf { it.isNotBlank() },
                aiProviderModel = optional("AI_PROVIDER_MODEL", "claude-sonnet-4-5"),
                logLevel = optional("LOG_LEVEL", "DEBUG"),
                aiTutorMessagesPerMinute = optional("AI_TUTOR_MESSAGES_PER_MINUTE", "20").toInt(),
                aiTutorMessagesPerDay = optional("AI_TUTOR_MESSAGES_PER_DAY", "200").toInt(),
                aiProviderMaxResponseTokens = rangedInt("AI_PROVIDER_MAX_RESPONSE_TOKENS", 1024, 1..8192),
                aiProviderTimeoutSeconds = rangedInt("AI_PROVIDER_TIMEOUT_SECONDS", 120, 5..600),
            )
        }
    }

    /** Safe for logging — never includes secret values. */
    fun redactedSummary(): String = "AppConfig(mongoDatabaseName=$mongoDatabaseName, " +
        "mediaStorageRoot=$mediaStorageRoot, corsAllowedOrigins=$corsAllowedOrigins, " +
        "aiProviderConfigured=${aiProviderApiKey != null}, logLevel=$logLevel)"

    /** One decision point, reused by both the DI binding and the startup log — PHASE_6_SYSTEM_DESIGN.md § 19.2. */
    fun aiProviderMode(): String = if (aiProviderApiKey != null) "anthropic" else "stub"
}

/**
 * Minimal local-only `.env` loader (no external dependency). Never overrides a value already
 * present in the real process environment — `.env` is strictly a local-development convenience,
 * never a substitute for real environment configuration in any environment where one is already set.
 */
private object DotEnv {
    fun load(): Map<String, String> {
        val fromEnv = System.getenv().toMutableMap()
        val envFile = File(".env").takeIf { it.exists() } ?: File("../.env").takeIf { it.exists() }
        if (envFile != null) {
            envFile.readLines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
                val idx = trimmed.indexOf('=')
                if (idx <= 0) return@forEach
                val key = trimmed.substring(0, idx).trim()
                val value = trimmed.substring(idx + 1).trim().removeSurrounding("\"")
                fromEnv.putIfAbsent(key, value)
            }
        }
        return fromEnv
    }
}
