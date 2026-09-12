import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Task 1 scaffolded the module with no Ktor/Koin/multiplatform-settings dependencies wired up —
// those land with the code that actually uses them in later tasks (see
// execution/PHASE_3_KMP_PLAN.md). Task 2 wired in kotlinx-serialization only, for the wire-contract
// envelope/error types. Task 3 adds the Ktor client itself (core/content-negotiation/logging in
// commonMain, OkHttp in androidMain, Darwin in iosMain — the latter unwired below, see the
// `sourceSets {}` comment) plus `ktor-client-mock`/kotlinx-coroutines-core for commonTest. Task 15
// adds Koin (DI) to commonMain and the iOS framework export + host-guarded SKIE config below. This
// module has NO UI dependency, ever (ADR-002).
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    // Declared with `apply false` so the plugin marker resolves cleanly on every host (harmless,
    // no Kotlin/Native compilation is triggered by resolution alone) but is never actually applied
    // except on macOS — see the host-guarded `apply(plugin = ...)` call at the bottom of this file
    // and its comment for the full "why" (disclosed limitation B1: SKIE requires Kotlin/Native
    // codegen, which requires a macOS host; this machine is Windows).
    alias(libs.plugins.skie) apply false
}

/** See the host-guarded SKIE `apply(plugin = ...)` call at the bottom of this file. */
val isMacOs = OperatingSystem.current().isMacOsX

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // iOS targets require a macOS host to actually compile/link — they configure but are never
    // built on this Windows machine. `kotlin.native.ignoreDisabledTargets=true` in
    // gradle.properties is what allows Gradle to configure the build at all here.
    //
    // Task 15: `binaries.framework {}` configures the exported `shared.xcframework` shape
    // (baseName/isStatic/exported dependencies) per `execution/PHASE_3_KMP_PLAN.md` Task 15 AC —
    // this configuration itself is plain Gradle DSL evaluation (no Kotlin/Native compiler
    // invocation), so it stays reachable/buildable on Windows even though the framework it
    // describes is never actually linked here (disclosed limitation B1).
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "shared"
            isStatic = true
            // kotlinx.datetime.Instant/Duration types appear in the public façade's use-case
            // signatures (e.g. PlaybackSource.expiresAt) — exporting kotlinx-datetime lets Xcode's
            // generated Swift interface reference `Instant`/`Duration` directly instead of Swift
            // seeing an opaque, unusable Kotlin type. kotlinx-coroutines and kotlinx-serialization
            // are deliberately NOT exported: Flow/StateFlow are consumed through SKIE's generated
            // AsyncSequence/closure bridging rather than the raw Kotlin type, and no serialization
            // type (DTOs, `kotlinx.serialization.json.Json`) is part of the façade's public
            // surface at all (only domain/use-case types are — see MentoraSdk.kt).
            export(libs.kotlinx.datetime)
        }
    }

    jvmToolchain(21)

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.client.logging)
                // Task 13: the first task that needs genuine Instant arithmetic (playback-url
                // near/past-expiry comparison), not just round-tripping an opaque ISO-8601 string
                // for display (Tasks 6/9/11's `createdAt`/`courseCompletedAt`/`issuedAt` convention).
                // `kotlinx.datetime.Instant` is `@Serializable` out of the box, so `MediaDto`'s
                // `expiresAt: Instant` field decodes directly with no custom serializer.
                // Task 15: promoted to `api` (not `implementation`) because `Instant`/`Duration`
                // appear in public façade signatures (`PlaybackSource.expiresAt`,
                // `RefreshPlaybackUrlUseCase`'s buffer) and the iOS framework `export(...)`s it
                // above — Kotlin/Native requires an exported dependency to be an `api` dependency
                // of the exporting source set.
                api(libs.kotlinx.datetime)
                // Task 15: Koin DI graph (`di/` package) + the `MentoraSdk` façade that resolves
                // use cases from it. Never part of the public façade's own signatures (`Module`/
                // `Koin`/`KoinApplication` stay internal-wiring-only — see MentoraSdk.kt), so this
                // stays `implementation`, not `api`, and is not exported to the iOS framework.
                implementation(libs.koin.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.ktor.client.mock)
                // Task 5: SessionManager's single-flight-refresh concurrency test.
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.ktor.client.okhttp)
                // Task 4: PreferenceStore (non-secret AppLocale/ThemePreference) is backed by
                // multiplatform-settings' SharedPreferencesSettings — plain SharedPreferences is
                // explicitly fine here (nothing secret). TokenStorage (secrets) never uses this.
                implementation(libs.multiplatform.settings)
                // Task 4: AndroidTokenStorage encrypts tokens with an Android-Keystore-resident
                // AES-GCM key before persisting the ciphertext via Preferences DataStore — see
                // AndroidTokenStorage's kdoc for why this was chosen over
                // androidx.security:security-crypto's EncryptedSharedPreferences.
                implementation(libs.androidx.datastore.preferences)
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        // iosMain is intentionally NOT referenced here: with `iosArm64()`/`iosSimulatorArm64()`
        // disabled by `kotlin.native.ignoreDisabledTargets=true` on this Windows host, Kotlin
        // never creates the iosMain source set object at configuration time (only its on-disk
        // directory exists, per src/iosMain/). Re-verified empirically for Task 3: temporarily
        // adding `val iosMain by getting { dependencies { implementation(libs.ktor.client.darwin) } }`
        // fails configuration on this machine with "KotlinSourceSet with name 'iosMain' not found" —
        // the same failure mode Task 1 already found. So `HttpClientEngineFactory.ios.kt` exists on
        // disk (per the plan's disclosed limitation B1) with its Darwin-engine `actual`, but its
        // `ktor-client-darwin` dependency is not wired into this build script. A macOS host picking
        // this up will need to add the `iosMain`/`iosTest` source sets back (which requires the iOS
        // targets to be enabled there) and add `implementation(libs.ktor.client.darwin)` to
        // `iosMain`'s dependencies. Task 4 adds the same caveat for `IosTokenStorage`/
        // `IosPreferenceStore`: they need `implementation(libs.multiplatform.settings)` added to
        // `iosMain`'s dependencies on that future macOS host (`IosTokenStorage`'s Keychain code
        // uses only `platform.Security`/`platform.Foundation`, already available to Kotlin/Native
        // with no extra dependency).
    }
}

android {
    namespace = "com.mentora.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Task 15, disclosed limitation B1: SKIE (Swift Kotlin Interface Enhancer) rewrites the Kotlin/
// Native-generated Objective-C header into idiomatic Swift (sealed classes -> Swift enums,
// `suspend fun` -> `async`/`await`, `Flow`/`StateFlow` -> `AsyncSequence`) for the `shared.xcframework`
// this module's `binaries.framework {}` block (above) configures. It only ever does anything to a
// Kotlin/Native compilation, which requires a macOS host (Xcode toolchain) — this machine is
// Windows, so applying it here would either silently no-op in the best case or (per real-world
// reports for this exact plugin) emit warnings/fail eagerly during Gradle's plugin-application
// lifecycle in the worst case. Guarding the `apply(plugin = ...)` call itself (not just something
// downstream of it) on `isMacOs` is therefore load-bearing, not defensive style — it's the one
// thing standing between this task and an unconditional SKIE application the plan explicitly
// forbids. The plugin is still declared (`apply false`) in the `plugins {}` block above so its
// marker artifact resolves cleanly and the version is pinned in one place (`libs.versions.toml`)
// regardless of host; only the actual `Plugin.apply()` call is skipped on non-macOS.
if (isMacOs) {
    apply(plugin = "co.touchlab.skie")
}
