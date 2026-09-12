package com.mentora.shared.di

import com.mentora.shared.auth.IosTokenStorage
import com.mentora.shared.auth.TokenStorage
import com.mentora.shared.data.network.defaultHttpClientEngine
import com.mentora.shared.settings.IosPreferenceStore
import com.mentora.shared.settings.PreferenceStore
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.module.Module
import org.koin.dsl.module

// NOT compiled/verified on this Windows machine — see "Disclosed limitation B1" in
// execution/PHASE_3_KMP_PLAN.md. `iosMain` is not wired into `shared/build.gradle.kts`'s
// `sourceSets {}` on this host (see that file's comment) because Kotlin never creates the
// `iosMain` source-set object at all while `iosArm64()`/`iosSimulatorArm64()` are
// `kotlin.native.ignoreDisabledTargets`-disabled here — so this file exists on disk, written to
// the same `expect`/`actual`-boundary contract as `IosTokenStorage`/`IosPreferenceStore`, ready for
// a macOS host to wire back in (which also needs `implementation(libs.multiplatform.settings)` and
// `implementation(libs.ktor.client.darwin)` added to `iosMain`'s dependencies — already documented
// in `shared/build.gradle.kts`).

/**
 * The iOS half of the "platform module" shape [com.mentora.shared.di.initKoin]/
 * [com.mentora.shared.MentoraSdk.create] require — the direct analogue of `androidMain`'s
 * `platformModule(context: Context)`, minus the `Context` parameter since neither
 * [IosTokenStorage] (Keychain) nor [IosPreferenceStore] (`NSUserDefaults.standardUserDefaults`)
 * needs one. Phase 5's iOS entry point is expected to call
 * `MentoraSdk.create(environment, platformModule())`.
 */
fun platformModule(): Module = module {
    single<TokenStorage> { IosTokenStorage() }
    single<PreferenceStore> { IosPreferenceStore() }
    single<HttpClientEngine> { defaultHttpClientEngine() }
}
