# Mentora Mobile — `mobile/`

Phase 3 scope only: this covers building/testing the **`:shared` Kotlin Multiplatform (KMP) core
module** — the shared domain/network/session layer that Phase 4 (Android) and Phase 5 (iOS) will
each build a real app on top of. There is no Android app, no iOS app, and no UI of any kind in this
directory yet — that's Phase 4/5's job. See `execution/PHASE_3_KMP_PLAN.md` for the full task-by-task
plan this module was built from, and `mobile/shared/README.md` for module-level (package layout,
façade, platform-consumption) documentation.

Phase 4 (Android) Task 1 has since added a bare `:androidApp` module scaffold — see
`execution/PHASE_4_ANDROID_PLAN.md` for the full 20-task Phase 4 plan; this README's "Layout" section
below reflects the scaffold, not yet any real screens.

## What this is

`mobile/` is its own **independent Gradle root** (`rootProject.name = "mentora-mobile"`, its own
Gradle wrapper), a **sibling build to `backend/` and `web/`** — not a composite/multi-module build
with either of them. The three share only a wire contract (`execution/INTEGRATION_CONTRACT.md`),
never compiled code. `mobile/settings.gradle.kts` declares two subprojects, `:shared` and
`:androidApp` (`:iosApp` is added later, in Phase 5).

## Prerequisites

- **JDK 21** (Temurin or equivalent) — same requirement as `backend/`. Verify: `java -version`.
- **Android SDK** with platform `android-36` and `build-tools 36.0.0` installed (`compileSdk = 36`).
  No global Gradle/Kotlin install is needed — the project vendors its own Gradle wrapper
  (`gradlew`/`gradlew.bat`); always invoke through the wrapper.
- **`mobile/local.properties`** (gitignored, never committed) pinning `sdk.dir` to your Android SDK
  install, e.g.:
  ```properties
  sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
  ```
  This is required on a shell where `ANDROID_HOME` isn't set (the case on this project's dev
  machine) — without it, Gradle cannot locate the SDK to configure the `androidTarget()`.
- **A macOS host, for iOS work only** — Kotlin/Native `iosArm64`/`iosSimulatorArm64` and the SKIE
  Gradle plugin both require macOS. See "iOS/SKIE limitation" below; nothing here requires macOS to
  build/test the Android-facing parts of `:shared`.

## Build

```
./gradlew.bat :shared:assembleDebug
```

Builds the Android target of `:shared` (an AAR, not an app — `:shared` has no UI/app shell). This
also configures (but does not compile/link) the iOS targets — see the iOS limitation below.

## Run the fast, deterministic test suite (no backend needed)

```
./gradlew.bat :shared:testDebugUnitTest
```

249 tests, 100% offline/deterministic — `commonTest` (Ktor `MockEngine`, fakes, `kotlin.test`) plus
the rest of `androidUnitTest` (plain JVM, reflection/architecture-boundary checks). No local backend,
no MongoDB, no network access required or attempted. This is the suite every Phase 3 task's own
quality gate ran, and the one any future change to `:shared` should keep green without needing
anything else running.

## Run the live integration test separately (needs the local backend running)

```
./gradlew.bat :shared:liveBackendIntegrationTest
```

Runs `LiveBackendIntegrationTest` alone, in its own isolated JVM/Gradle `Test` task — **deliberately
excluded from `:shared:testDebugUnitTest`** (see `mobile/shared/build.gradle.kts`'s comment and
`execution/DECISIONS_LOG.md` D77 for why: it was found to intermittently fail with a spurious parse
error only when sharing a JVM with ~249 other tests, never in isolation, and a live-network test
doesn't belong in the fast/offline suite regardless of that flake). It drives the real `MentoraSdk`
façade through the full student journey (register → login → categories → search → course detail →
checkout → progress → quiz → certificate → learning-path follow → AI Tutor stream → locale →
logout) against your actual local backend + seeded MongoDB.

Start the backend first — see `backend/README.md` for setup/run instructions (MongoDB replica-set
conversion, `.env`, `seedDemoData`). If the backend isn't reachable at `http://localhost:8080`, this
test **skips cleanly** via `org.junit.Assume` rather than failing — it is safe to leave un-run if you
don't have the backend up.

## iOS/SKIE limitation (disclosed, not silently glossed over)

This is a **Windows** development machine. Kotlin/Native's `iosArm64`/`iosSimulatorArm64` targets and
the SKIE Gradle plugin (which turns the generated Objective-C header into idiomatic Swift) both
require a macOS host (Xcode toolchain) to actually compile/link/apply. On this machine:

- `iosArm64()`/`iosSimulatorArm64()` targets **configure** (Gradle can evaluate the build script,
  `binaries.framework {}` shape and all) via `kotlin.native.ignoreDisabledTargets=true` in
  `gradle.properties`, but are **never compiled or linked** here.
- `iosMain`'s `actual` implementations (`IosTokenStorage` — Keychain, `IosPreferenceStore` —
  `NSUserDefaults`, the Darwin Ktor engine `actual`) exist on disk, written to the same
  `expect`/`actual`-shaped boundary as their Android counterparts, but **the `iosMain` Kotlin source
  set itself is not wired into `shared/build.gradle.kts`'s `sourceSets {}`** on this host — Kotlin
  never creates that source-set object while the iOS targets are disabled. A future macOS host will
  need to re-add the `iosMain`/`iosTest` source-set blocks (enabling the iOS targets there first) and
  the two dependencies noted in that file's comments (`ktor-client-darwin`, `multiplatform-settings`).
- SKIE is applied **host-guarded** (`if (isMacOs) apply(plugin = "co.touchlab.skie")` in
  `shared/build.gradle.kts`) so the Windows build still configures cleanly without ever invoking it.

**None of this has been compiled, linked, or SKIE-verified on this machine.** Phase 5 (iOS) will
need a real macOS host regardless of anything done in Phase 3 — this is an environmental fact, not a
Phase 3 defect. See `execution/PHASE_HANDOFF.md`'s Phase 3 entry and `execution/DECISIONS_LOG.md` D69
for the full account.

## Layout

```
mobile/
├── settings.gradle.kts          rootProject.name = "mentora-mobile"; include(":shared", ":androidApp")
├── build.gradle.kts              plugin aliases, apply false
├── gradle.properties             kotlin.native.ignoreDisabledTargets=true, android.useAndroidX=true
├── gradle/libs.versions.toml      version catalog (Kotlin/AGP/Ktor/serialization/datetime/coroutines/Koin/Compose)
├── gradlew / gradlew.bat / gradle/wrapper/
├── local.properties               GITIGNORED — sdk.dir (create this yourself, see Prerequisites)
├── .gitignore
├── shared/                        the one KMP module — see mobile/shared/README.md
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/            domain models, use cases, networking, auth/session, façade — all platforms
│       ├── commonTest/            MockEngine + fakes, offline/deterministic
│       ├── androidMain/           actual TokenStorage/PreferenceStore, OkHttp engine, Koin androidModule
│       ├── androidUnitTest/       plain-JVM tests incl. the isolated LiveBackendIntegrationTest
│       └── iosMain/               actual TokenStorage (Keychain)/PreferenceStore, Darwin engine — NOT
│                                   wired into the build on this Windows host (see iOS limitation above)
└── androidApp/                    the Android application module — Phase 4, see execution/PHASE_4_ANDROID_PLAN.md
    ├── build.gradle.kts           application + Kotlin Android + Compose compiler plugins; depends on :shared
    └── src/
        ├── main/                  MainActivity (bare placeholder screen, Task 1), manifest, res/
        └── debug/                 debug-only network security config (cleartext to 10.0.2.2 only)
```

## Further reading

- `mobile/shared/README.md` — module-level documentation: package layout, the 4 `ApiEnvironment`
  targets, how Phase 4/5 are expected to consume `:shared` (via `MentoraSdk` only), and the 10 façade
  domains.
- `execution/PHASE_3_KMP_PLAN.md` — the full 17-task plan this module was built from.
- `execution/INTEGRATION_CONTRACT.md` § 12 — the Phase 3 as-built wire-contract notes (CSRF header,
  no-cookie-jar, `?language=` semantics, the `AUTH_TOKEN_INVALID`/`AUTH_TOKEN_EXPIRED` drift, etc.).
- `execution/PHASE_HANDOFF.md` — the Phase 3 → Phase 4 handoff entry (what Phase 4 depends on, known
  limitations, what not to redo).
- `execution/DECISIONS_LOG.md` D69–D77 — every Phase 3 implementation-time decision and its rationale.
