package com.mentora.shared.data.network

import io.ktor.client.engine.HttpClientEngine

/**
 * Produces the platform's default Ktor HTTP engine — OkHttp on Android, Darwin on iOS. Tests
 * inject Ktor's `MockEngine` directly into [HttpClientFactory.create] instead of going through
 * this `expect`/`actual`.
 */
expect fun defaultHttpClientEngine(): HttpClientEngine
