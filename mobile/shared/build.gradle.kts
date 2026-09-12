import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Task 1 scaffolded the module with no Ktor/Koin/multiplatform-settings dependencies wired up —
// those land with the code that actually uses them in later tasks (see
// execution/PHASE_3_KMP_PLAN.md). Task 2 wires in kotlinx-serialization only, for the wire-contract
// envelope/error types — no networking (Ktor client) dependency yet. This module has NO UI
// dependency, ever (ADR-002).
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
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidMain by getting
        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        // iosMain is intentionally NOT referenced here: with `iosArm64()`/`iosSimulatorArm64()`
        // disabled by `kotlin.native.ignoreDisabledTargets=true` on this Windows host, Kotlin
        // never creates the iosMain source set object at configuration time (only its on-disk
        // directory exists, per src/iosMain/). See "Disclosed limitation B1" in the Phase 3 plan.
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
