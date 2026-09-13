// Task 1: bare :androidApp scaffold — no navigation, no theme system, no design tokens yet (later
// tasks per execution/PHASE_4_ANDROID_PLAN.md). Depends on :shared only; Compose is wired via the
// Kotlin 2.0+ K2 compiler plugin (org.jetbrains.kotlin.plugin.compose) rather than the pre-2.0
// composeOptions.kotlinCompilerExtensionVersion mechanism, which no longer exists. Dependency set is
// deliberately minimal — no Media3/ExoPlayer, Coil, or Navigation-Compose yet.
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinCompose)
}

android {
    namespace = "com.mentora.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mentora.android"
        // Same minSdk as :shared's androidTarget (mobile/shared/build.gradle.kts) — the app can
        // never go lower than the library it depends on.
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // Task 4 (Part D): the real-Keystore instrumented test needs the AndroidJUnitRunner to
        // run as connectedDebugAndroidTest.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        // Task 4: MentoraApplication.onCreate reads BuildConfig.DEBUG to decide
        // enableNetworkLogging for MentoraSdk.create — AGP 8.x no longer generates BuildConfig by
        // default, so this must be opted into explicitly.
        buildConfig = true
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    // Task 4 (F5 fix-up): LocaleController directly imports androidx.core.os.LocaleListCompat —
    // was previously only a transitive dependency (via activity-compose).
    implementation(libs.androidx.core.ktx)
    // Task 4: AppSessionViewModel + its Compose collection at the MainActivity smoke-test call site.
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // Task 4: MentoraApplication.onCreate builds its own platform Module (see the class's kdoc,
    // G1) — it references Ktor's HttpClientEngine type directly for that binding's declared type,
    // same as shared/di/PlatformModule.android.kt does. Same Ktor version as :shared's catalog
    // entry, no new artifact.
    implementation(libs.ktor.client.core)

    // Task 15's Koin DI graph lives in :shared; these two add Android-specific
    // startKoin/androidContext() wiring and Compose's koinViewModel()/koinInject() helpers — actual
    // DI wiring itself is Task 4, not this task.
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Task 2: MentoraTokens.kt drift test — parses design-tokens.json/theme-*.json directly at
    // test time and compares against the generated Kotlin constants (JUnit4, the AGP
    // testDebugUnitTest default; kotlinx-serialization-json is already in this catalog for
    // :shared's own use, not a new dependency).
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)

    // Task 4 (Part D): real-Keystore instrumented test (connectedDebugAndroidTest) — test-only.
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.core)

    // Task 5 (component kit A): Compose UI instrumented tests — see libs.versions.toml's comment on
    // these two entries for why this is androidTest (real device), not Robolectric.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
