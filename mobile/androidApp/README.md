# Mentora Android — Local Run Instructions

Phase 4 scope: the native Android app (`:androidApp`, Jetpack Compose + Material3), consuming
`mobile/shared` (the Phase 3 KMP module) via `MentoraSdk`. It talks to the Phase 1 backend over HTTP —
nothing here runs standalone. See `backend/README.md` for backend setup; this file assumes that's
already done. `mobile/shared/README.md` documents the KMP module `:androidApp` consumes — do not
re-derive its contract here.

## Prerequisites

- **JDK 11+** (the module's own `sourceCompatibility`/`jvmTarget`). Verify: `java -version`.
- **Android SDK** — `compileSdk`/`targetSdk` 36, `minSdk` 26. Installed via Android Studio, or the
  command-line tools alone (`platform-tools`, `platforms;android-36`, `build-tools;36.0.0`, plus an
  emulator system image — see below).
- An Android emulator (AVD) or a physical device running API 26+. This phase has been built and
  verified throughout against a single AVD, `Chatting_Pixel_8_API_36` — creating one with a similar
  profile (Pixel 8, API 36, Google APIs) is the closest match to what's actually been tested.
- The Phase 1 backend running and reachable at `http://10.0.2.2:8080` **from the emulator's own
  perspective** (`10.0.2.2` is the AVD's alias for the host machine's `localhost` — see
  `backend/README.md`). A physical device instead needs the host's real LAN IP; there is no separate
  build config for this today — the base URL is currently hardcoded for the emulator case only (a
  disclosed, deliberate limitation — see `execution/PHASE_4_ANDROID_PLAN.md` § 6 / `DECISIONS_LOG.md`
  D53, no production backend exists anywhere in this project).

## Install / open

Open `mobile/` (the Gradle root — **not** `mobile/androidApp/`) in Android Studio and let it sync, or
work from the command line:

```
cd mobile
./gradlew.bat --version     # Windows; ./gradlew on macOS/Linux — confirms the 8.11.1 wrapper resolves
```

No manual dependency install step — Gradle resolves everything (including `koin-android`, Compose BOM
2024.10.01, Media3/ExoPlayer, Coil) on first sync/build.

## Run

```
cd mobile
./gradlew.bat :androidApp:assembleDebug      # build the debug APK
./gradlew.bat :androidApp:installDebug       # install onto a running/connected emulator or device
```

Or simpler for local iteration: run the `androidApp` configuration directly from Android Studio with an
emulator/device selected — this builds, installs, and launches in one step, and is what most of this
phase's own manual verification used.

**Start the emulator and backend first, in this order**, before installing/launching:

```
emulator -avd Chatting_Pixel_8_API_36          # from <Android SDK>/emulator/
cd backend && ./gradlew.bat run                # or gradlew on macOS/Linux — see backend/README.md
```

Every screen beyond the login/register shell is unusable without the backend reachable — most screens
fail closed with a real, localized `ErrorState` (never a crash) if it isn't.

Seeded demo accounts (from `backend/README.md`'s `seedDemoData` task) work against this app exactly as
they do against the website: `student1@mentora.dev` / `MentoraDemo1` (also `student2@`,
`instructor1@`/`instructor2@`, `admin@` — the last two have no dedicated mobile surface in this phase,
Instructor/Admin Web only).

## Quality gates

```
cd mobile
./gradlew.bat :shared:testDebugUnitTest           # KMP shared-module unit tests — must stay 249/249 (Phase 3 regression guard, unchanged by any Phase 4 task)
./gradlew.bat :androidApp:testDebugUnitTest       # androidApp JVM unit tests (ViewModels, pure logic) — 241/241 as of Task 19
./gradlew.bat :androidApp:compileDebugKotlin :androidApp:compileDebugUnitTestKotlin :androidApp:compileDebugAndroidTestKotlin   # compile-only check, fast
```

## Instrumented tests (real emulator/device required)

```
./gradlew.bat :androidApp:connectedDebugAndroidTest
```

106/106 passing as of the Phase 4 final acceptance review, run against the real
`Chatting_Pixel_8_API_36` emulator **with the real local backend already running** — a large fraction
of these tests hit real network calls (register/login/enroll/etc. against the live backend, never
mocked, matching this project's testing philosophy throughout every phase). Running this suite with no
backend reachable fails ~10 tests with `NETWORK_ERROR`/`Failed to connect to /10.0.2.2:8080` — that is
an environment gap, not a code defect; start the backend first (see Run, above) and re-run.

Also note the backend's `auth` route group is rate-limited to 10 requests/minute per
(`backend/.../plugins/RateLimiting.kt`). Since a large share of this suite registers a fresh real
account per test, running it back-to-back multiple times in quick succession (e.g. repeated manual
re-runs during debugging) can trip `RateLimitedAuth` (HTTP 429) on whichever test happens to register
next — this is backend rate-limiting working as designed, not a code defect. If you see a single
isolated `RateLimitedAuth` failure, wait ~1 minute for the limiter to refill and re-run.

Known, disclosed flake pattern (recorded repeatedly across Tasks 12-19, e.g. `DECISIONS_LOG.md`
D84/D89/D92): a handful of screenshot/pixel-capture-based component tests can flake under host-resource
contention on a long-running emulator session. Confirmed pattern: kill and relaunch the AVD fresh, then
re-run — this has cleanly resolved every occurrence so far without a code change.

## Project layout (Phase 4, Android-only)

```
mobile/
  shared/                         KMP module (Phase 3) — MentoraSdk, domain models, local persistence. See mobile/shared/README.md.
  androidApp/
    src/main/kotlin/com/mentora/android/
      MainActivity.kt             app entry point — wraps content in LocalizedContent (Task 18)
      navigation/                 MentoraNavHost, Destinations, AuthGate — the 5-tab shell + auth gate (Task 6)
      locale/                     LocalizedContent, LocaleController, StringsParityTest source
      theme/                      MentoraTheme, MentoraTokens (generated), MentoraDimens
      ui/components/              shared component kit — atoms (Task 5) + cards/state patterns/sheets (Task 8)
      ui/{home,explore,coursedetails,checkout,mylearning,courseplayer,quiz,certificates,
          learningpathdetails,aitutor,profile}/   one package per screen, Screen + ViewModel pair
      playback/                   MediaPlaybackController (ExoPlayer/Media3), Task 13
    src/main/res/values{,-ar}/strings.xml   EN/AR string resources — kept in exact key + format-specifier parity (StringsParityTest.kt)
    src/test/kotlin/              JVM unit tests (ViewModels, pure logic, StringsParityTest)
    src/androidTest/kotlin/       instrumented tests (Compose UI tests, real-device playback/session tests)
```

See `execution/PHASE_4_ANDROID_PLAN.md` for the full task-by-task plan this phase was built against, and
`execution/CURRENT_STATUS.md` / `execution/DECISIONS_LOG.md` for the as-built account of every task,
including every disclosed gap and deliberate scope decision.

## Known limitations (disclosed, not blocking)

- No password-change endpoint exists in the backend — Settings has no such field (same gap inherited
  from Web's D44, and Phase 1's own scope).
- No real certificate image asset exists anywhere in the backend — `CertificateCard`/
  `CertificateDetailScreen` render a token-driven placeholder "document" presentation instead (same
  disclosed precedent as Web's D42).
- Icon set is a hand-drawn inline `ImageVector` port of Web's own 42-icon placeholder set (D80) — not
  the real Material Symbols Rounded font this project's design system ultimately specifies.
- Arabic typography uses `FontFamily.SansSerif` as a placeholder — no Noto Sans Arabic (or equivalent)
  font asset exists in the repo yet.
- The backend base URL is hardcoded for the Android emulator's `10.0.2.2` alias — a physical device
  needs a manual source change to point at the host's real LAN IP (see Prerequisites, above).
- Course Player's "Ask AI Tutor" affordance full-navigates to the standalone AI Tutor tab rather than a
  docked/contextual panel — `ux/RESPONSIVE_BEHAVIOR.md § 9`'s docked-panel variant was never built for
  either Web or Android (Phase 2 Task 9's own disclosed gap, inherited here unchanged).
