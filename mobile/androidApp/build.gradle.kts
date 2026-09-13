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
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)

    // Task 15's Koin DI graph lives in :shared; these two add Android-specific
    // startKoin/androidContext() wiring and Compose's koinViewModel()/koinInject() helpers — actual
    // DI wiring itself is Task 4, not this task.
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
}
