// Task 1: bare :androidApp scaffold — no navigation, no theme system, no design tokens yet (later
// tasks per execution/PHASE_4_ANDROID_PLAN.md). Depends on :shared only; Compose is wired via the
// Kotlin 2.0+ K2 compiler plugin (org.jetbrains.kotlin.plugin.compose) rather than the pre-2.0
// composeOptions.kotlinCompilerExtensionVersion mechanism, which no longer exists. Dependency set is
// deliberately minimal — no Media3/ExoPlayer, Coil yet.
// Task 6: navigation-compose's type-safe (`@Serializable`) routes need the Kotlin serialization
// compiler plugin applied to THIS module (previously only :shared applied it) to generate
// serializers for `navigation/Destinations.kt`'s route types.
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.kotlinSerialization)
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
        // T11 fix-up: `NoOpApplicationTestRunner` (androidTest source set) substitutes a plain
        // `Application` for `.MentoraApplication` in the instrumented-test process — see that
        // class's own kdoc for the real cross-session bug this closes.
        testInstrumentationRunner = "com.mentora.android.NoOpApplicationTestRunner"
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

    // Task 6: the navigation shell — see gradle/libs.versions.toml's androidxNavigation comment for
    // the exact version and why. kotlinx-serialization-core backs navigation-compose's type-safe
    // (@Serializable) route model in navigation/Destinations.kt.
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.core)
    // T6 fix-up (Finding 2): pendingNavIntent needs to survive rotation/process death via a
    // rememberSaveable Saver that (de)serializes the sealed Destination route to a JSON string —
    // kotlinx-serialization-json was already in this catalog (and already implementation-wired in
    // :shared for the identical reason), just not previously wired as a main implementation()
    // dependency of this module (only testImplementation, below). No new artifact/version
    // introduced.
    implementation(libs.kotlinx.serialization.json)

    // Task 15's Koin DI graph lives in :shared; these two add Android-specific
    // startKoin/androidContext() wiring and Compose's koinViewModel()/koinInject() helpers — actual
    // DI wiring itself is Task 4, not this task.
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Task 8 (component kit B): CourseThumbnail loads a real thumbnailUrl via Coil, falling back to
    // the governed motif/gradient artwork system on load failure — see gradle/libs.versions.toml's
    // coil comment for the version choice.
    implementation(libs.coil.compose)

    // Task 13 C1 (D85 Decision 1/2, "Dependencies to add"): the ExoPlayer/Media3 playback
    // controller — see gradle/libs.versions.toml's media3-* comments for the version choice and
    // why exactly these three artifacts (and no others).
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.common)
    implementation(libs.media3.datasource)

    // Task 2: MentoraTokens.kt drift test — parses design-tokens.json/theme-*.json directly at
    // test time and compares against the generated Kotlin constants (JUnit4, the AGP
    // testDebugUnitTest default; kotlinx-serialization-json is already in this catalog for
    // :shared's own use, not a new dependency).
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
    // Task 9: ExploreViewModel's debounce/pagination logic is a plain JVM unit test — Dispatchers
    // .setMain (this artifact) swaps viewModelScope's Main dispatcher for a TestDispatcher with no
    // real Android Looper/Robolectric needed, the same standard pattern :shared's own commonTest
    // already relies on (see shared/build.gradle.kts's identical dependency, not a new artifact).
    testImplementation(libs.kotlinx.coroutines.test)

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
