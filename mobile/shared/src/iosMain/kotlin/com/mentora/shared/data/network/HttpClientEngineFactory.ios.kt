package com.mentora.shared.data.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

// ⚠ Not compiled/verified on this Windows machine — see "Disclosed limitation B1" in
// execution/PHASE_3_KMP_PLAN.md. iosArm64/iosSimulatorArm64 require a macOS host.
actual fun defaultHttpClientEngine(): HttpClientEngine = Darwin.create()
