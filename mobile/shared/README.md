# `:shared` — Mentora KMP Shared Mobile Core

The one Kotlin Multiplatform module in `mobile/`. Owns every piece of student-facing domain logic,
networking, session/auth handling, and secure/non-secure local storage that Android (Phase 4) and iOS
(Phase 5) both need — **no UI, ever** (`architecture/ADR-002-kmp-sharing-boundary.md`). Zero
Compose/SwiftUI/`android.*` UI imports exist anywhere in `commonMain` — enforced by a real
file-content-scan test (`NoUiImportBoundaryTest`), not just convention.

See `mobile/README.md` for build/test commands and prerequisites. This file documents the module
itself: package layout, environment targets, and — most importantly — **how Phase 4/5 are expected to
consume it**.

## Package layout

```
commonMain/kotlin/com/mentora/shared/
├── MentoraSdk.kt          the public façade (see "How to consume this module" below)
├── config/                ApiEnvironment (base URL + timeouts), ApiEnvironment.resolveUrl()
├── auth/                  SessionManager, AuthState, TokenStorage (interface), AuthPlugin (401→refresh→retry)
├── settings/              PreferenceStore (interface), AppLocale, ThemePreference, resolveInitialLocale()
├── domain/
│   ├── model/             User, Course, Section, Lesson, Enrollment, CourseProgress, Quiz, Certificate,
│   │                      LearningPath, AiConversation, AiQuickAction, PlaybackSource, ... (pure data)
│   ├── validation/        EmailValidator, PasswordValidator, FieldError
│   └── usecase/           one class per action, one package per domain (auth/, catalog/, enrollment/,
│                          progress/, quiz/, certificate/, learningpath/, media/, aitutor/, user/)
├── data/
│   ├── network/           HttpClientFactory, ApiClient, envelope/error DTOs, ApiResult, CursorPage,
│   │                      localeQueryParam(), per-feature DTOs (data/network/dto/)
│   └── repository/        interface + internal Impl per domain (auth/, catalog/, enrollment/, progress/,
│                          quiz/, certificate/, learningpath/, media/, aitutor/, user/)
├── playback/              LessonPlaybackController — INTERFACE ONLY, no implementation
└── di/                    initKoin(), one Koin module file per domain
```

Plus `androidMain`/`iosMain` (platform `actual`s for `TokenStorage`, `PreferenceStore`, the Ktor
`HttpClientEngine`, and each platform's `platformModule(...)` factory), and `commonTest`/
`androidUnitTest` (MockEngine + fakes, plus the live-backend test — see `mobile/README.md`).

Package convention: `com.mentora.shared.<layer>.<feature>`, per `architecture/REPOSITORY_STRUCTURE.md § 7`.

## The 4 `ApiEnvironment` targets

Defined in `config/ApiEnvironment.kt` — the single source of truth for where the networking layer
points; no URL is hardcoded anywhere else in `shared`:

| Factory | Base URL | Use case |
|---|---|---|
| `ApiEnvironment.androidEmulator()` | `http://10.0.2.2:8080` | Android emulator's alias for the host machine's `localhost` |
| `ApiEnvironment.iosSimulator()` | `http://localhost:8080` | iOS simulator shares the host machine's network namespace |
| `ApiEnvironment.lan(host, port = 8080)` | `http://<host>:<port>` | A physical device on the same LAN as a backend running on a dev machine |
| `ApiEnvironment.custom(baseUrl)` | caller-supplied | A fully custom override — e.g. a future staging/production base URL |

Every route lives under `/api/v1/...` on top of whichever `baseUrl` is chosen; `ApiEnvironment` itself
holds only the bare host root, plus connect/request/socket timeouts (`ApiTimeouts`, generous
15s/30s/30s defaults).

## How Phase 4/5 are expected to consume this module

**Only via `MentoraSdk.create(environment, platformModule, enableNetworkLogging)`.** This is
enforced structurally, not just documented: every one of the 10 repository `Impl` classes across
`data/repository/` is `internal` visibility, so nothing outside `shared` can construct or reference
one directly, and `MentoraSdk` never exposes an `ApiClient`, `HttpClient`, or Koin type on its own
public surface (verified by direct code read at Task 15, D76).

```kotlin
// Android (e.g. Application.onCreate)
val sdk = MentoraSdk.create(
    environment = ApiEnvironment.androidEmulator(),
    platformModule = platformModule(applicationContext), // androidMain's platformModule(context: Context)
)

// iOS (app entry point)
let sdk = MentoraSdk.create(
    environment: .iosSimulator(),
    platformModule: platformModule(), // iosMain's no-arg platformModule()
)
```

`MentoraSdk` resolves lazily, once per instance — callers must hold onto (and reuse) the same
`MentoraSdk` instance for the app's lifetime rather than constructing a fresh one per call, since at
least one use case (`ReportPlaybackPositionUseCase`'s playback-heartbeat throttle) has genuine
per-instance state that only behaves correctly through the façade (D76).

### The platform module each side must supply

`initKoin`/`MentoraSdk.create` need a `platformModule: Module` binding exactly three things `shared`
itself cannot construct (it has no `Context`, no Keychain API, no HTTP engine of its own):
`TokenStorage`, `PreferenceStore`, and the Ktor `HttpClientEngine`.

- **Android** — `androidMain`'s `platformModule(context: Context): Module` (`di/PlatformModule.android.kt`)
  binds `AndroidTokenStorage(context)` (AES-256-GCM under an Android-Keystore-resident key + Jetpack
  DataStore — never `EncryptedSharedPreferences`, see `DECISIONS_LOG.md` D70), `AndroidPreferenceStore(context)`
  (plain `SharedPreferences` via `multiplatform-settings` — fine for non-secret locale/theme only),
  and the OkHttp engine.
- **iOS** — `iosMain`'s no-arg `platformModule(): Module` (`di/PlatformModule.ios.kt`) binds
  `IosTokenStorage()` (Keychain), `IosPreferenceStore()` (`NSUserDefaults.standardUserDefaults`), and
  the Darwin engine. **Wired into the build as of Phase 5 Task T1** — `build.gradle.kts` now declares
  `iosMain`'s `ktor-client-darwin`/`multiplatform-settings` dependencies and an `iosTest` source set
  (`kotlin("test")`), using the null-safe `sourceSets.findByName("iosMain")?.dependencies { ... }`
  form rather than `val iosMain by getting {}` (the latter is known to fail Gradle configuration on a
  Windows host with `kotlin.native.ignoreDisabledTargets=true` — `findByName` degrades to a no-op
  there instead). An `XCFramework("shared")` holder was also added, registering
  `assembleXCFramework`/`assembleSharedDebugXCFramework`/`assembleSharedReleaseXCFramework`. All of
  this is **Gradle-configuration-verified on this Windows host only** (`:shared:testDebugUnitTest`
  stays 249/249, `:shared:assembleDebug` stays clean, the three XCFramework tasks are listed, zero
  new configuration warnings) — nothing has actually been compiled or linked for iOS yet, since that
  requires a macOS/Xcode toolchain this host doesn't have. Real `iosMain` compilation, linking, and a
  produced `shared.xcframework` are still pending the Phase 5 **MC-1** Mac checkpoint
  (`execution/PHASE_5_IOS_IMPLEMENTATION_PLAN.md` § 3).

Never construct a repository, `ApiClient`, or `HttpClient` directly from Phase 4/5 code — everything
needed is reachable through `MentoraSdk`'s 10 façade properties.

## The 10 façade domains

Each `MentoraSdk` property is a thin wrapper exposing only its domain's already-built use cases as
callable methods (`sdk.auth.login(...)`, `sdk.catalog.searchCourses(...)`), never a repository or
network type:

| Façade | Covers |
|---|---|
| `sdk.auth` | Register/login/logout, session state (`AuthState` `StateFlow`), refresh, cold-start restore |
| `sdk.user` | `GET`/`PATCH /users/me`, locale set/sync (login-overwrites-local vs. register-seeds-account), theme (local-only) |
| `sdk.catalog` | Categories, course search/filter/pagination, course detail + curriculum (embedded in the same detail response — there is no separate `/curriculum` endpoint) |
| `sdk.enrollment` | Checkout preview, demo checkout completion (idempotent), enrollment list, "My Learning" composition |
| `sdk.progress` | Course/lesson progress, resume-course resolution, mark-lesson-complete + auto-advance, playback-position heartbeat |
| `sdk.quiz` | Load quiz (no `isCorrect` pre-submission), submit attempt, latest attempt lookup |
| `sdk.certificates` | List/get certificates — opaque `MTR-...` id passthrough, no issuance action (server-triggered only) |
| `sdk.learningPaths` | List (unpaginated) / detail (guest-safe) / follow / unfollow |
| `sdk.media` | Lesson-video playback-url resolution + refresh, thumbnail URL resolution |
| `sdk.aiTutor` | Paged conversation history, streaming message send, the 5 quick-action identities (no localized text — Phase 4/5 supplies that) |

## `LessonPlaybackController` is an interface only

`playback/LessonPlaybackController.kt` defines the platform-agnostic contract a real video player
binds to (`prepare`/`play`/`pause`/`seekTo`/`currentPosition`/`duration`/`state`, all as `Flow`s) —
**`shared` ships no implementation of it**: no ExoPlayer, no AVPlayer, not even a stub beyond a
trivial, clearly-labeled shape-verification test double in `commonTest`. Phase 4 (Android) must supply
its own ExoPlayer/Media3-backed implementation; Phase 5 (iOS) must supply its own AVPlayer-backed
implementation. Both receive an already-absolute, already-resolved stream URL from
`GetLessonPlaybackSourceUseCase`/`RefreshPlaybackUrlUseCase` (`sdk.media`) and must use it as-is,
attaching no `Authorization` header of their own — the URL's own `?token=` query parameter is the
entire auth mechanism the backend's `/media/{id}/stream` route checks. Lesson duration is never
available from any API response (`architecture`-confirmed absent field) — it comes from the player at
runtime only, once loaded.

## Further reading

- `mobile/README.md` — build/test commands, prerequisites, iOS/SKIE limitation.
- `execution/INTEGRATION_CONTRACT.md` § 12 — the full Phase 3 as-built wire contract this module
  implements against.
- `execution/PHASE_3_KMP_PLAN.md` — the task-by-task plan and every acceptance criterion.
- `execution/DECISIONS_LOG.md` D69–D77 — every implementation-time decision.
