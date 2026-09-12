import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Task 1 scaffolded the module with no Ktor/Koin/multiplatform-settings dependencies wired up —
// those land with the code that actually uses them in later tasks (see
// execution/PHASE_3_KMP_PLAN.md). Task 2 wired in kotlinx-serialization only, for the wire-contract
// envelope/error types. Task 3 adds the Ktor client itself (core/content-negotiation/logging in
// commonMain, OkHttp in androidMain, Darwin in iosMain — the latter unwired below, see the
// `sourceSets {}` comment) plus `ktor-client-mock`/kotlinx-coroutines-core for commonTest. This
// module has NO UI dependency, ever (ADR-002).
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
}

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
    iosArm64()
    iosSimulatorArm64()

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
                implementation(libs.kotlinx.datetime)
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
