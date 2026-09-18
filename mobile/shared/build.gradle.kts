import org.gradle.api.tasks.testing.Test
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

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
    // Task 1 (Phase 5): the `XCFramework("shared")` holder registers the umbrella
    // `assembleXCFramework` task plus per-configuration `assembleSharedDebugXCFramework`/
    // `assembleSharedReleaseXCFramework` tasks that fat-package both iOS targets' frameworks into
    // one `shared.xcframework` for Xcode to consume — this is Gradle DSL evaluation only (no
    // Kotlin/Native compiler invocation), so like `binaries.framework {}` below it configures and
    // stays reachable on Windows even though nothing is actually linked here (disclosed limitation
    // B1). Verified by applying this exact change on this host: the registered task names are
    // `assembleXCFramework`, `assembleSharedDebugXCFramework`, `assembleSharedReleaseXCFramework` —
    // there is no `assembleSharedXCFramework`.
    val xcf = XCFramework("shared")
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
            xcf.add(this)
        }
    }

    // Task T4c (D100): `:shared:iosSimulatorArm64Test` has no default simulator device configured —
    // probed directly on this host by querying the registered task's `device`/`deviceId` properties:
    // both are completely unset, and querying either throws "Cannot query the value... because it
    // has no value available." This is mandatory configuration, not optional convenience — the test
    // task cannot run at all without it. Driven by a Gradle property so
    // `.github/workflows/ios-ci.yml`'s "Select iOS Simulator destination" step can pass the exact
    // device name it detected as actually available on the runner image
    // (`-Pmentora.ios.testDevice="${SIM_DEVICE_NAME}"` — see that workflow step for where
    // `SIM_DEVICE_NAME` comes from). "iPhone 16" is the fallback for when that property is absent
    // (e.g. running this task locally), matching the first entry in the workflow's own `preferred`
    // simulator-name list so local and CI behavior agree.
    iosSimulatorArm64 {
        testRuns["test"].deviceId = (findProperty("mentora.ios.testDevice") as String?) ?: "iPhone 16"
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
        // Phase 5 Task 1: on THIS Windows host, `iosArm64()`/`iosSimulatorArm64()` are still
        // disabled (`kotlin.native.ignoreDisabledTargets=true`), so Kotlin still never creates the
        // `iosMain`/`iosTest` source set objects at configuration time here — `val iosMain by
        // getting {}` still fails configuration with "KotlinSourceSet with name 'iosMain' not
        // found" (re-verified; same failure Task 1/3 of Phase 3 already found). `findByName(...)` is
        // therefore used instead of `by getting`: it returns null (silently skipped) on this host
        // and only resolves to a real source set on a macOS host where the iOS targets are actually
        // enabled — which is exactly what lets this wiring exist now without breaking the Windows
        // build.
        sourceSets.findByName("iosMain")?.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.multiplatform.settings)
        }
        sourceSets.findByName("iosTest")?.dependencies {
            // Phase 5 Task 1b's IosTokenStorageTest/FakeKeychain (iosTest source set) need plain
            // kotlin.test assertions, same as every other source set's test dependency.
            implementation(kotlin("test"))
        }
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

// Task 16: `LiveBackendIntegrationTest` is the one test in this whole module that makes real HTTP
// calls against a live local backend — every other test (`commonTest` + the rest of
// `androidUnitTest`) is deterministic and offline (MockEngine/fakes/reflection only), per this
// project's own standing testing-strategy requirement ("prefer not requiring live backend for
// every unit test"). It was found, while reviewing Task 16, to intermittently fail with a spurious
// `ApiErrorCode.Unknown("UNPARSEABLE_RESPONSE")` at `httpStatus = 200` — but ONLY when run as part
// of the full ~250-test `testDebugUnitTest` suite in the same forked JVM (observed failing 3 of 4
// full-suite runs), NEVER once across many runs in isolation. The exact root cause inside that
// shared JVM (OkHttp/Ktor engine state, GC/JIT warm-up, or something else entirely) was not
// conclusively identified, but the trigger condition — coexisting with ~249 other test classes in
// one JVM — is clear and directly avoidable: excluding it from `testDebugUnitTest` and giving it
// its own dedicated `Test` task means it always runs in a fresh, isolated JVM, which is both a
// clean fix for the observed flake AND the architecturally correct split regardless (a live-network
// test does not belong in the fast, deterministic, offline suite every other task's quality gate —
// and every prior Phase 3 commit — has relied on staying green with no backend running).
// `:shared:testDebugUnitTest` and `:shared:testReleaseUnitTest` therefore no longer include this
// one test (the plain `:shared:test` / root `gradlew test` aggregate runs both variants, so both
// needed the exclusion — testReleaseUnitTest was originally missed and remained exposed to the
// same flake); run `:shared:liveBackendIntegrationTest` deliberately when the local backend is up
// (it still skips cleanly, via `org.junit.Assume`, if it isn't).
// Deferred to `afterEvaluate`: AGP/KGP finish registering and fully configuring their own
// `testDebugUnitTest`/`testReleaseUnitTest` tasks (test class dirs, classpath) only once this whole
// build script's evaluation completes — referencing them any earlier races that setup.
project.afterEvaluate {
    val debugUnitTest = tasks.named<Test>("testDebugUnitTest")
    debugUnitTest.configure {
        exclude("**/LiveBackendIntegrationTest.class")
    }
    tasks.named<Test>("testReleaseUnitTest").configure {
        exclude("**/LiveBackendIntegrationTest.class")
    }

    tasks.register<Test>("liveBackendIntegrationTest") {
        group = "verification"
        description = "Runs LiveBackendIntegrationTest alone, in its own JVM, against the local " +
            "backend (skips cleanly via org.junit.Assume if it isn't reachable). Excluded from " +
            "testDebugUnitTest and testReleaseUnitTest — see the comment above this task for why."
        testClassesDirs = debugUnitTest.get().testClassesDirs
        classpath = debugUnitTest.get().classpath
        include("**/LiveBackendIntegrationTest.class")
        outputs.upToDateWhen { false } // a live-backend check should never be treated as cacheable.
    }
}
