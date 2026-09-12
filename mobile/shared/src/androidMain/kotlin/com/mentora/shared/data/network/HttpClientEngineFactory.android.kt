package com.mentora.shared.data.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

actual fun defaultHttpClientEngine(): HttpClientEngine = OkHttp.create()
