# Phase 3 — KMP Shared Mobile Core: Implementation Plan

**Status:** Authoritative task plan for Phase 3, derived by the `architect` subagent on 2026-09-12
from `architecture/KMP_ARCHITECTURE.md`, `architecture/adr/ADR-002-kmp-sharing-boundary.md` and the
other locked architecture/product/ux docs, `execution/INTEGRATION_CONTRACT.md`, and the actual
backend route source (not docs alone). Logged as `DECISIONS_LOG.md` D69. Read this file before
resuming any Phase 3 task — do not re-derive acceptance criteria from memory. This is an execution
planning document, not a locked architecture/product/ux/design-system file — it may be amended as
implementation surfaces new facts, with the amendment recorded in `DECISIONS_LOG.md`.

Per-task status is tracked in `execution/CURRENT_STATUS.md`'s "PHASE 3 — Task Breakdown" table;
this file is the acceptance-criteria detail that table points to.

---

## 1. Current state (verified, not assumed, at plan time)

Phase 3 starts from **absolute zero** — no `mobile/` directory existed anywhere in the repo or git
history at plan time. `architecture/REPOSITORY_STRUCTURE.md § 4` is authoritative for where it goes:
**`mobile/shared`**, with `mobile/settings.gradle.kts` as its own Gradle root (`include(":shared")`,
`:androidApp` added later in Phase 4) — a **sibling Gradle build to `backend/`**, not a shared
multi-module build with it. `execution/MASTER_IMPLEMENTATION_PLAN.md`'s M4 slice is: domain models,
use cases, Ktor Client networking, auth/session orchestration, the secure-storage `expect`/`actual`
boundary, and a platform-agnostic playback interface — no Android/iOS app shell, no UI.

## 2. Load-bearing constraints found in the real backend source (not just docs)

| Constraint | Source | Consequence |
|---|---|---|
| iOS targets (`iosArm64`/`iosSimulatorArm64`) and SKIE both require macOS; this machine is Windows. | environment | `iosMain` `actual` code is written per the `expect`/`actual` boundary but **never compiled or tested** in Phase 3. Disclosed limitation, not silently glossed. Phase 5 needs a Mac regardless. |
| Android SDK has `android-36`/`build-tools 36.0.0` only (no 35) on this machine; `ANDROID_HOME` unset in shell. | filesystem check | `compileSdk = 36`; AGP must support it; `mobile/local.properties` (gitignored) pins `sdk.dir` explicitly to `%LOCALAPPDATA%\Android\Sdk`. |
| Backend JWT resolver **prefers the `mentora_access_token` cookie over the `Authorization` header**. | `plugins/Security.kt:27-31` | The KMP Ktor client **must not install `HttpCookies`** — a stale rotated cookie would silently outrank a freshly-refreshed Bearer token. |
| `requireCsrfHeader()` applies uniformly to every mutating route, including `/auth/*`, and demands the literal value `X-Requested-With: mentora-web`. | `common/Csrf.kt:14-16` | Mobile sends the literal header value `mentora-web` on every mutating request. No backend change (see Decision C1). |
| `POST /auth/refresh` accepts the refresh token from cookie **or** JSON body `{ refreshToken }`. | `auth/routes/AuthRoutes.kt:69-70` | Mobile refresh works over the body path — no backend change needed. |
| `429` responses have **no JSON envelope and no error code** (Ktor `RateLimit` plugin, no `StatusPages` handler for it). | `plugins/RateLimiting.kt` | Contradicts `API_CONTRACT.md § 4`. Shared error mapper **synthesizes** the code from status + path (see Decision C2). |
| `GET /learning-paths` and `GET /categories` return **plain unpaginated arrays**, not `respondPage`. | `LearningPathRoutes.kt:20`, `CategoryRoutes.kt:22` | Modeled as `List<T>`, not `CursorPage<T>`. |
| `?language=` exists on `GET /courses`, `GET /courses/{id}`, `GET /courses/{id}/checkout`, `GET /learning-paths/{id}` — on the **list** it is both a metadata resolver **and** a result-set filter. | `CourseRoutes.kt:37,47`, `CourseRepository.kt:103`, D57 | Mobile threads the active UI locale through on reads, mirroring the already-shipped web behavior (see Decision C3). Not previously documented in `INTEGRATION_CONTRACT.md` — added there in Task 17. |
| **No lesson duration is exposed by any endpoint.** `MediaDocument.durationSeconds` exists but no response DTO carries it. | `CourseService.kt:29-32`, `MediaService.kt:35-36` | Shared `Lesson` model has **no duration field**; duration comes from the player at runtime for the current lesson only (see Decision C4). |
| `explicitNulls = false` server-side. | `plugins/Serialization.kt:13` | Shared `Json` uses `ignoreUnknownKeys = true` and treats every optional as defaulting to `null`. |
| Mobile is **Student-only** — Instructor/Admin are Web-only. | `USER_ROLES.md`, `MVP_SCOPE.md § 2` | Zero instructor/admin/course-write/media-upload/quiz-editor endpoints in shared. |
| Backend versions: Kotlin 2.0.21(-ish, backend pins 2.0.20 via wrapper drift — verify exact at Task 1), Ktor 3.0.1, kotlinx-serialization 1.7.3, kotlinx-datetime 0.6.1, coroutines 1.9.0, Koin. | `backend/build.gradle.kts` | Align Ktor/serialization/datetime with these where multiplatform artifacts exist, to minimize wire-shape drift. Kotlin version for the KMP module may need to be newer than the backend's for AGP/compileSdk 36 compatibility — that's fine, they're separate Gradle builds. |

## 3. Decisions made (with rationale)

- **D-A.** `mobile/` is its own Gradle build (own wrapper), not folded into a repo-root composite with `backend/`. The two share no compiled code, only a wire contract — a composite build would needlessly couple JVM and AGP toolchains.
- **D-B.** Common tests run via the Android unit-test task (`:shared:testDebugUnitTest`) — the only JVM-executable path with `androidTarget()` + iOS targets declared. `commonTest` uses `kotlin.test` + Ktor `MockEngine` only (no JVM-only APIs); **live-backend** integration tests live in `androidUnitTest` (plain JVM, real HTTP, Ktor OkHttp engine).
- **D-C.** Two separate storage abstractions: `TokenStorage` (secrets — Keystore/EncryptedPrefs on Android, Keychain on iOS, per `AUTH_SECURITY.md § 4`) and `PreferenceStore` (non-secret locale/theme, via `multiplatform-settings`). Never share one mechanism for both — the default `multiplatform-settings` Android backing is plain `SharedPreferences`, which would violate the locked "never store secrets in plain prefs" rule.
- **D-D.** AI Tutor quick actions: `shared` owns the identity/shape (the fixed 5-action set, which require lesson context, the `courseId`+`lessonContextId` pairing rule); the **platform UI supplies the localized prompt text**. Satisfies both `ADR-002`'s "no localized strings in shared" and `AI_TUTOR_ARCHITECTURE.md § 7`'s "content is localized."
- **D-E.** Streaming AI responses (`POST /ai-tutor/conversation/messages`, raw chunked `text/plain`) bypass the generic `ApiClient` via a dedicated `Flow<String>` path — same precedent as Phase 2's `streamAiMessage()` (D43).
- **D-F.** Phase 3 makes **zero backend changes**. Every conflict below is absorbed client-side or explicitly escalated.

### Resolved conflicts (none required stopping — each has a backend-unchanged, scope-preserving resolution)

- **C1 (CSRF header value).** `ADR-006`/`AUTH_SECURITY.md § 10` say mobile "is not CSRF-exposed," but the backend enforces the literal `X-Requested-With: mentora-web` on every mutating route uniformly. **Resolution: mobile sends `mentora-web` verbatim on every POST/PATCH/PUT/DELETE.** No backend change (a backend widening to accept additional client values was considered and explicitly rejected for Phase 3 — it would reopen `backend/` after Phase 1 was declared COMPLETE, for a cosmetic-only gain).
- **C2 (429 has no envelope/code).** Shared error mapper synthesizes `RATE_LIMITED_AUTH` (for `/auth/*` paths) or `RATE_LIMITED_AI_TUTOR` (for `/ai-tutor/*` paths) from the bare status code.
- **C3 (`?language=` is both resolver and filter on the course list).** Mobile mirrors the already-shipped web behavior exactly (D57): pass the active UI locale on reads, omit on writes. This is "preserve existing localized-metadata behavior," not a new product decision.
- **C4 (no lesson duration in the API).** Shared `Lesson` omits duration entirely; a future curriculum-sheet UI (Phase 4/5) gets it from the player after a lesson loads, not from the API. Flagged in the Phase 4 handoff as a UI-only limitation, not fixed here.
- **C5 (`categories`/`learning-paths` unpaginated).** Modeled as `List<T>`; documented in `INTEGRATION_CONTRACT.md` (Task 17).
- **C6 (`IMPLEMENTATION_ROADMAP.md` M4 bundles shared+Android+iOS; `MASTER_IMPLEMENTATION_PLAN.md` splits Phase 3 as shared-only).** The phase-split document is authoritative for sequencing, per `CURRENT_STATUS.md`/`PHASE_HANDOFF.md` precedent.

### Disclosed limitation (not a conflict — an environmental fact)

- **B1.** iOS targets and SKIE cannot be compiled or verified on this Windows machine. `iosMain` `actual`s are written to the `expect`/`actual` boundary but untested here. Phase 5 will need a macOS host regardless of Phase 3 choices.

## 4. Module structure

```
mobile/
├── settings.gradle.kts              rootProject.name = "mentora-mobile"; include(":shared")
├── build.gradle.kts                 plugin aliases, apply false
├── gradle.properties                kotlin.native.ignoreDisabledTargets=true, android.useAndroidX=true
├── gradle/libs.versions.toml         version catalog
├── gradlew / gradlew.bat / gradle/wrapper/
├── local.properties                 GITIGNORED — sdk.dir
├── .gitignore
└── shared/
    ├── build.gradle.kts
    └── src/
        ├── commonMain/kotlin/com/mentora/shared/
        │   ├── MentoraSdk.kt
        │   ├── config/          MentoraEnvironment, ApiEnvironment
        │   ├── domain/
        │   │   ├── model/       User, Role, Category, Course, CourseSummary, Section, Lesson,
        │   │   │                CourseTranslation, PriceDisplay, LessonResource, Enrollment,
        │   │   │                CheckoutPreview, CourseProgress, Quiz, QuizQuestion, QuizOption,
        │   │   │                QuizAttemptResult, AttemptBreakdown, Certificate,
        │   │   │                CertificateDetail, LearningPath, LearningPathDetail,
        │   │   │                AiConversation, AiMessage, AiQuickAction, PlaybackSource
        │   │   ├── validation/  EmailValidator, PasswordValidator, FieldError
        │   │   └── usecase/     one class per action (see task table)
        │   ├── data/
        │   │   ├── network/     HttpClientFactory, ApiClient, envelope DTOs, ApiResult,
        │   │   │                ApiError/ApiErrorCode, CursorPage, per-feature DTOs + mappers,
        │   │   │                AuthPlugin (bearer + single-flight refresh)
        │   │   └── repository/  interfaces + Impl (auth, user, catalog, enrollment, progress,
        │   │                    quiz, certificate, learningPath, media, aiTutor)
        │   ├── auth/            SessionManager, AuthState, TokenStorage (expect), AuthTokens
        │   ├── settings/        PreferenceStore (expect), AppLocale, ThemePreference
        │   ├── playback/        LessonPlaybackController (interface only — no impl)
        │   └── di/              sharedModule, initKoin()
        ├── commonTest/kotlin/com/mentora/shared/   MockEngine + fake repositories
        ├── androidMain/kotlin/com/mentora/shared/  actual TokenStorage, actual PreferenceStore,
        │                                           Android Ktor engine, Koin androidModule
        ├── androidUnitTest/kotlin/                 LIVE-backend contract tests (real HTTP)
        └── iosMain/kotlin/com/mentora/shared/       actual TokenStorage (Keychain),
                                                     actual PreferenceStore, Darwin engine
                                                     ⚠ NOT COMPILED ON THIS MACHINE
```

Package convention: `com.mentora.shared.<layer>.<feature>` per `REPOSITORY_STRUCTURE.md § 7`.

## 5. Task sequence

Every task: implement → `:shared:testDebugUnitTest` + `:shared:assembleDebug` green → review diff
(scope-only) → commit as its own checkpoint → update `CURRENT_STATUS.md` → continue automatically.

### Task 1 — `mobile/` Gradle root + `:shared` KMP scaffold
**AC:** `mobile/settings.gradle.kts` (`rootProject.name = "mentora-mobile"`, `include(":shared")` only, no `:androidApp` yet) · `shared/build.gradle.kts` declares `androidTarget()`, `iosArm64()`, `iosSimulatorArm64()` · all 5 source sets exist per the package tree above · `kotlin.native.ignoreDisabledTargets=true` so the build configures on Windows · `./gradlew :shared:assembleDebug` and `:shared:testDebugUnitTest` succeed with one placeholder test · `libs.versions.toml` pins Kotlin/AGP(≥ needed for compileSdk 36)/Ktor 3.0.1/kotlinx-serialization 1.7.3/kotlinx-datetime 0.6.1/coroutines 1.9.0/Koin/multiplatform-settings · `.gitignore` covers `local.properties`/`build/`/`.gradle/`/`*.xcframework` · **no Compose/SwiftUI/Android-UI dependency anywhere**.
**Must NOT:** touch `backend/`/`web/`/locked docs; create `androidApp`/`iosApp`/`infra/`; apply SKIE; add a `jvm()` target.
**Tests:** one trivial `commonTest`.
**Depends on:** none.

### Task 2 — Wire contract primitives: envelope, error taxonomy, `ApiResult`, `CursorPage`
**AC:** `ApiSuccess<T>{data, meta{requestId, nextCursor?}}` / `ApiError{error{code,message,fields?}, meta}` match `common/ApiResponse.kt` byte-exactly · `ApiResult<T>` sealed `Success`/`Failure(code,message,fields,httpStatus)` · `ApiErrorCode` covers every code the backend actually emits (grep-verified: VALIDATION_ERROR, AUTH_INVALID_CREDENTIALS, AUTH_TOKEN_EXPIRED, AUTH_TOKEN_INVALID, FORBIDDEN_ROLE, FORBIDDEN_NOT_OWNER, FORBIDDEN_NOT_ENROLLED, FORBIDDEN_CSRF, COURSE_NOT_FOUND, SECTION_NOT_FOUND, LESSON_NOT_FOUND, CATEGORY_NOT_FOUND, QUIZ_NOT_FOUND, ATTEMPT_NOT_FOUND, CERTIFICATE_NOT_FOUND, LEARNING_PATH_NOT_FOUND, MEDIA_NOT_FOUND, USER_NOT_FOUND, EMAIL_ALREADY_REGISTERED, CATEGORY_IN_USE, RATE_LIMITED_AUTH, RATE_LIMITED_AI_TUTOR, INTERNAL_ERROR, plus `Unknown(raw)`) · 429-without-body synthesized per C2 · `CursorPage<T>{items, nextCursor}` · shared `Json{ignoreUnknownKeys=true, explicitNulls=false, isLenient=false}` · `Failure.message` never shown to UI as the contract (code is).
**Must NOT:** invent codes not emitted by the backend; add `ALREADY_ENROLLED` (never thrown).
**Tests:** envelope round-trip both shapes; omitted-optional handling; unknown-code fallback; 429 synthesis; `nextCursor` present/absent.
**Depends on:** T1.

### Task 3 — Environment config + Ktor `HttpClient` factory + `ApiClient`
**AC:** `ApiEnvironment` (baseUrl+timeouts) with named presets — Android emulator `http://10.0.2.2:8080`, iOS simulator `http://localhost:8080`, LAN device, custom override · `HttpClientFactory` installs `ContentNegotiation`+`HttpTimeout`+debug-only `Logging` with token/Authorization redaction · **`HttpCookies` NOT installed** (commented, citing `Security.kt:27-31`) · `X-Requested-With: mentora-web` on every request (default request) · retry only for idempotent GETs, never for mutating verbs · `ApiClient` exposes `get/post/patch/delete`→`ApiResult<T>` and `getPage`→`ApiResult<CursorPage<T>>`, unwrapping via T2 · one client configured centrally, not per-repository · `actual` engines: OkHttp (android), Darwin (ios).
**Must NOT:** install `HttpCookies`; add auth/bearer logic (T5); log bodies at INFO.
**Tests (MockEngine):** success unwrap; 4xx/500 typed failures; CSRF header on every verb; GET retried, POST not; no `Cookie` header ever sent.
**Depends on:** T2.

### Task 4 — `TokenStorage` / `PreferenceStore` `expect`/`actual` boundary
**AC:** `expect interface TokenStorage` (save/read/clear `AuthTokens`) · Android `actual` Keystore/EncryptedPrefs-backed (or Keystore-wrapped DataStore if EncryptedSharedPreferences is unavailable/deprecated at implementation time — record the choice), iOS `actual` Keychain-backed · never plain SharedPreferences/UserDefaults; tokens never logged · `expect interface PreferenceStore` over `multiplatform-settings` for `AppLocale`(en/ar)/`ThemePreference`(light/dark/system), non-secret only · `PreferenceStore` exposes active locale as `StateFlow<AppLocale>` · `resolveInitialLocale(systemLocales: List<String>): AppLocale` is a **shared pure function** (Arabic if preferred+supported, else English), platform injects the system locale list · `FakeTokenStorage`/`FakePreferenceStore` in `commonTest`.
**Must NOT:** store tokens via `multiplatform-settings`' default backing; implement localized string resources; read `Locale.current` inside `commonMain`.
**Tests:** `resolveInitialLocale` table; preference round-trip + Flow emission via fakes.
**Depends on:** T1.

### Task 5 — `SessionManager`, auth plugin (401→single-flight refresh→retry), auth repository + use cases + validation
**AC:** `AuthState` sealed `Unknown`/`Authenticated(user)`/`Unauthenticated` as `StateFlow` · auth plugin attaches `Authorization: Bearer` from `SessionManager` · on `401 AUTH_TOKEN_EXPIRED`: call `POST /auth/refresh` with `{refreshToken}` in body exactly once, then retry — **N concurrent 401s coalesce into exactly 1 refresh call** (mutex/Deferred single-flight), asserted by test · refresh failure ⇒ clear `TokenStorage` + `Unauthenticated`, no retry, no forced navigation from shared · every successful register/login/refresh persists the **rotated** refresh token · `/auth/refresh` and `/auth/logout` exempt from the 401-interception loop · endpoints matched exactly to `AuthRoutes.kt`: `POST /auth/register`→201, `POST /auth/login`→200, `POST /auth/refresh`, `POST /auth/logout` — all four need the CSRF header, all rate-limited 10/min · login/register's embedded `user` is a **narrower `SessionUser`** shape (no avatarMediaId/createdAt) vs. full `GET /users/me` · `PasswordValidator`(≥8 chars, ≥1 letter, ≥1 digit)/`EmailValidator`(trim+lowercase+≤254 chars+backend regex) mirror the server, never replace it · use cases: `RegisterUseCase`, `LoginUseCase`, `LogoutUseCase`, `RefreshSessionUseCase`, `ObserveAuthStateUseCase`, `RestoreSessionUseCase`, each depending only on the `AuthRepository` interface · login failure surfaces one generic `AUTH_INVALID_CREDENTIALS`, no field hint.
**Must NOT:** persist tokens anywhere but `TokenStorage`; log a token value; navigate/emit UI intents from shared; implement forgot/reset password (Post-MVP); read `Set-Cookie`.
**Tests:** happy register/login/logout; 401→refresh→retry; concurrent-401 single-flight; refresh-fail clears state; rotated token persisted; no refresh loop on `/auth/refresh` itself; validator tables.
**Depends on:** T3, T4.

### Task 6 — User profile, preferences & locale sync
**AC:** `GET /users/me`→full `User` (distinct from `SessionUser`) · `PATCH /users/me` accepts only `{name?, preferredLocale?}` (locale restricted to en/ar; name ≤120 chars non-blank) · `SetLocaleUseCase` writes `PreferenceStore` and, when authenticated, PATCHes `preferredLocale`; login's account locale overwrites local, register/first-login-after-guest sends the local value up as the initial account value · `SetThemeUseCase` is local-only (no backend field) · locale change never logs the user out.
**Must NOT:** build avatar upload (`avatarMediaId` is a dead field — D44); build password change (no endpoint); localize any string in shared.
**Tests:** profile mapping incl. omitted optionals; PATCH body contains only changed fields; login-overwrites-local vs. register-seeds-account precedence.
**Depends on:** T5.

### Task 7 — Catalog domain: categories, courses, curriculum, search/filter/pagination
**AC:** `GET /categories`→plain `List<Category>` (not CursorPage) · `GET /courses` (category/level/language/maxPrice/q/cursor/limit, **no `status` filter** — always published)→`CursorPage<CourseSummary>` exactly matching `CourseService.kt:39-43`'s fields · `GET /courses/{id}`→summary+`status`+`sections[]`+`translations: Map<String,CourseTranslation>`; `Section{sectionId,title,order,lessons[]}`; `Lesson{lessonId,title,description,order,videoMediaId?,resources[]}` — **no duration field** (C4) · `?language=` threaded from `PreferenceStore` on course list/detail/checkout-preview/learning-path-detail reads, per C3 · `contentLanguage` (course metadata) and UI locale are **separate types**, never conflated · `ratingSeed` documented as static seed data · `priceDisplay.amount` is unformatted integer minor units · a draft course returns 404 always, never 403, including for an already-enrolled student (D64) · use cases: `ListCategoriesUseCase`, `SearchCoursesUseCase`(paged), `GetCourseDetailsUseCase`, `GetCourseCurriculumUseCase`.
**Must NOT:** implement any course/section/lesson write endpoint; add an `isEnrolled` field (never implemented); address courses by slug.
**Tests:** DTO→domain mapping against a real 3-section/12-lesson seed course shape (D67/D68); query-string builder per filter; cursor paging across 2 pages; `?language=ar` resolution; sections/lessons arrive order-sorted.
**Depends on:** T3, T4 (locale), T5 (optional auth).

### Task 8 — Enrollment & demo checkout
**AC:** `GET /courses/{id}/checkout?language=`→`CheckoutPreview` · `POST /courses/{id}/checkout/complete`→`EnrollmentCompletion{enrollment, alreadyEnrolled}` — **201 first time, 200 every idempotent repeat, never an error** · `GET /enrollments`→`CursorPage<Enrollment>`, minimal shape, no embedded course detail · `GetMyLearningUseCase` composes enrollments→per-course detail **in shared** (accepted N+1 at seed scale, D40 precedent) · **zero payment vocabulary** anywhere in this task's diff, grep-verified · `source="demoCheckout"`, `status="active"` as typed values.
**Must NOT:** client-retry `checkout/complete` (T3 forbids POST retry); model `ALREADY_ENROLLED` as an error.
**Tests:** 201 vs 200 both `Success` with correct `alreadyEnrolled`; `GetMyLearningUseCase` composition against fakes; enrollment paging; payment-vocabulary grep check.
**Depends on:** T5, T7.

### Task 9 — Progress
**AC:** `GET /courses/{id}/progress`→`CourseProgress{courseId, completedLessonIds, currentLessonId?, currentPositionSeconds?, quizPassed?, completionPercent, courseCompletedAt?}`, lazily created · `POST .../lessons/{id}/complete` idempotent · `POST .../lessons/{id}/position` body `{positionSeconds}` non-negative, debounced/throttled once in shared, never retried · `403 FORBIDDEN_NOT_ENROLLED` is a typed, distinguishable failure · `ResumeCourseUseCase` resolves resume target from **server-authoritative** progress + curriculum order (currentLessonId else first incomplete lesson in section/lesson order) + currentPositionSeconds · `CompleteLessonUseCase` returns new progress **and** the auto-advance target (next lesson or course-finished) so both platforms can't diverge · optimistic updates only as transient reconciled-against-server values, server is authoritative.
**Must NOT:** compute `completionPercent` locally as source of truth; mark completion purely client-side; issue certificates (server-triggered).
**Tests:** resume resolution across all branches incl. stale `currentLessonId`; auto-advance at section boundary and last lesson; idempotent double-complete; 403 typing; heartbeat throttling.
**Depends on:** T7, T8.

### Task 10 — Quiz
**AC:** `GET /courses/{id}/quiz`→`Quiz{questions[{questionId,prompt,order,options[{optionId,text}]}]}` — `QuizOption` **has no `isCorrect` property at all** (structural, not just null) · `POST .../quiz/attempts` body `{answers:[{questionId,selectedOptionId}]}`→`QuizAttemptResult{score,passed,breakdown[{questionId,selectedOptionId?,correctOptionId,isCorrect}]}` — correctness revealed **only** post-submission · `GET .../quiz/attempts/latest`→same shape or 404 `ATTEMPT_NOT_FOUND` typed as "no attempt yet" · grading never computed client-side · 404 `QUIZ_NOT_FOUND` typed as "course has no quiz" (legit state — at least one seeded course has none) · `SubmitQuizUseCase` validates every question answered before sending.
**Must NOT:** implement quiz editor endpoints; add `isCorrect` to the pre-submission model; compute pass/fail locally.
**Tests:** structural assertion `isCorrect` cannot deserialize into the pre-submission option type; submit→result mapping; `ATTEMPT_NOT_FOUND`/`QUIZ_NOT_FOUND` typed states; incomplete-answer local validation.
**Depends on:** T7, T9.

### Task 11 — Certificates
**AC:** `GET /certificates`→`CursorPage<CertificateSummary>` · `GET /certificates/{id}`→`CertificateDetail`; another student's id→404 · `id` is the public `MTR-...` code, opaque, passed through verbatim, never reconstructed · snapshot fields (student/course/instructor names, completion date) are frozen-at-issuance, never re-resolved against live course data · **no issuance endpoint exists** — shared exposes no "issue certificate" action · share/download is UI-only, shared provides no export/PDF/image generation.
**Must NOT:** add issue/revoke action; generate a public verification URL.
**Tests:** paging; MTR-id passthrough; 404 typing; snapshot fields mapped verbatim.
**Depends on:** T9, T10.

### Task 12 — Learning Paths
**AC:** `GET /learning-paths`→plain `List<LearningPathSummary>` — not paginated, no `isFollowing` (route not under `authenticate`) · `GET /learning-paths/{id}?language=`→`LearningPathDetail{courses[], progressPercent?, isFollowing}`, optional-auth guest-safe, `progressPercent` null for guests · `POST`/`DELETE .../follow`→`{isFollowing}`, Student role, idempotent, CSRF header required · `progressPercent` is server-derived on read, never recomputed client-side · use cases: `FollowLearningPathUseCase`/`UnfollowLearningPathUseCase` · path courses preserve curated order.
**Must NOT:** model the list as `CursorPage`; synthesize `isFollowing` onto list items; implement path authoring.
**Tests:** unpaginated-array parsing; guest vs authenticated detail; follow/unfollow round-trip; order preservation.
**Depends on:** T7, T9.

### Task 13 — Media / lesson-video playback contract + platform playback interface
**AC:** `GET /media/{id}/playback-url` (auth required)→`PlaybackSource{url, expiresAt}`, url relative, ~5min validity · shared resolves the relative url to absolute against `ApiEnvironment.baseUrl`, once, centrally · the resolved stream url is handed to the platform player as-is — **no** `Authorization` header attached by the player (the `?token=` param is the auth); range requests supported server-side for scrubbing · `GET /media/{id}/file` is for public thumbnails/avatars only — a lessonVideo id through this route 404s by design, shared must never route a lesson video here · `RefreshPlaybackUrlUseCase` re-requests near/after `expiresAt` · `LessonPlaybackController` is an **interface only** in commonMain (`play/pause/seekTo/currentPosition/duration/state` as Flows) — **no implementation in shared**, ExoPlayer/AVPlayer are Phase 4/5 · duration comes from the player at runtime only (C4), never from the API.
**Must NOT:** implement `POST /media/uploads`; implement any player; download/cache video bytes.
**Tests:** relative→absolute URL resolution; expiry-refresh threshold logic; 403 typing on playback-url; thumbnail URL construction.
**Depends on:** T3, T7.

### Task 14 — AI Tutor (paged conversation + streaming send + quick actions)
**AC:** `GET /ai-tutor/conversation?cursor&limit`→non-standard envelope `{conversationId, messages[], nextCursor?}` (not a bare array) — modeled as its own type · `POST /ai-tutor/conversation/messages` body `{content, courseId?, lessonContextId?}` → chunked `text/plain` stream, exposed as `Flow<String>` (bypasses the generic `ApiClient`, per D-E) · `courseId`+`lessonContextId` are a strict pair — validated client-side before any network call · `content` ≤4000 chars non-blank, validated in shared · pre-stream failure = normal JSON envelope (403/404/429); mid-stream failure preserves partial text, surfaced as a stream error · `AiQuickAction` sealed/enum of exactly 5 (ExplainThisLesson, Summarize, GiveMeAnExample, QuizMe, WhatShouldILearnNext), each declaring whether it needs lesson context — **localized prompt text supplied by the platform UI**, not stored in shared (D-D) · `SendAiTutorMessageUseCase` has **zero dependency on `QuizRepository`**, asserted by test (QuizMe touches nothing quiz-shaped) · no AI provider SDK/key/model name anywhere in shared — every call goes through `/ai-tutor/*` · 429→`RATE_LIMITED_AI_TUTOR` · Phase 3 talks to the Phase 1 **stub** provider; Phase 6 changes only the backend Koin binding, wire contract unchanged.
**Must NOT:** store English quick-action prompt strings in shared; add voice I/O or proactive messages; create a QuizAttempt from any AI path; auto-retry a failed send.
**Tests:** non-standard envelope parsing; `Flow<String>` chunk accumulation via MockEngine streaming; pre-stream 403/429 typed; mid-stream failure preserves partial text; pairing validation (courseId without lessonContextId ⇒ local failure, zero network calls); content-length validation; structural assertion `SendAiTutorMessageUseCase` has no quiz dependency.
**Depends on:** T5, T7.

### Task 15 — Public façade, Koin wiring, iOS framework export + SKIE (host-guarded)
**AC:** `initKoin(environment, platformModule)` is the single entry point; every repository/use case/`SessionManager`/`TokenStorage`/`PreferenceStore` resolvable · a `MentoraSdk` façade (or documented "resolve use cases from Koin" convention) is the **only** surface Phase 4/5 consume — never a repository impl or `ApiClient` directly · a test/documented grep check proves `commonMain` imports nothing from Compose/SwiftUI/`android.*` UI packages/any UI framework · iOS framework config (`baseName="shared"`, `isStatic=true`, exported deps, SPM-consumable) guarded by a host check so Windows still configures · SKIE applied macOS-host-only, with a comment why · sealed types/enums used deliberately at the façade for clean Swift interop · named disclosed limitation: the iOS framework has never been built/SKIE-verified on this machine.
**Must NOT:** apply SKIE unconditionally; expose `HttpClient`/`ApiClient`/repository impls publicly; add a UI dependency.
**Tests:** Koin module-resolution check; no-UI-import boundary check; `:shared:assembleDebug` + `:shared:testDebugUnitTest` green.
**Depends on:** T5–T14.

### Task 16 — Live integration verification against the running local backend
**AC:** an `androidUnitTest` suite (plain JVM, real HTTP, no mocks) runs the full student journey against the locally running backend + seeded MongoDB: register→login→categories→search→course detail (3 sections/12 lessons)→checkout preview→complete checkout→progress→complete a lesson→playback-url→quiz→submit→certificate→learning-path follow→AI Tutor stream→PATCH locale→logout · skips cleanly with a clear message if the backend isn't reachable (`GET /healthz`) · token refresh exercised for real (force-expire, assert 1 refresh + 1 retry + success) · secure storage verified by inspection (persisted token matches issued value; nothing token-shaped in a plain preference store) · `?language=ar` verified against the real Arabic UX seed course · test accounts use a distinct greppable pattern (e.g. `@kmp.mentora.test`) and are cleaned up after · any real contract drift discovered is documented in `INTEGRATION_CONTRACT.md`, not silently fixed in `backend/` · respects the 10/min auth rate limit (known Phase 2 flake source).
**Must NOT:** modify `backend/`; mock anything; leave test data in the dev database.
**Depends on:** T15.

### Task 17 — Documentation & Phase 3 → Phase 4 handoff
**AC:** `mobile/README.md` + `mobile/shared/README.md` (prerequisites, build/test commands, the 4 API_BASE_URL targets, how Phase 4/5 consume the module) · `execution/INTEGRATION_CONTRACT.md` gains a Phase 3 as-built section (CSRF header value, no-cookie-jar rule, 429-without-envelope, `?language=` semantics on all 4 read endpoints, unpaginated categories/learning-paths, AI Tutor's non-standard envelope + stream, absent lesson duration) — additive only · `execution/PHASE_HANDOFF.md` gains a Phase 3 entry in the file's fixed structure · `execution/DECISIONS_LOG.md` continues from D69 with implementation-time decisions · `execution/CURRENT_STATUS.md` updated: Phase 3 → COMPLETE, exact resume point for Phase 4 · known limitations recorded honestly: iOS/SKIE never compiled here; androidMain actuals tested as JVM unit tests only (no instrumented/emulator run); mobile CSRF-header-value oddity; no offline cache · `git log --name-only` gate: no Phase 3 commit touched `architecture/`, `product/`, `ux/`, `design-system/`, `design-review-locked/`, `backend/`, or `web/`.
**Must NOT:** edit any locked doc.
**Depends on:** T16.

## 6. Scope exclusions confirmed against `product/MVP_SCOPE.md`

No Android/iOS UI · no wishlist/favorites/save-for-later · no notifications · no real payments (grep-verified in T8) · no real AI provider integration (all traffic through the Phase 1 stub) · no discussion/Q&A · no offline downloads/local relational cache (no SQLDelight) · no Instructor/Admin surfaces · no localized UI strings/design tokens/navigation in shared · no backend/web/locked-doc changes · no SSO/password-reset/avatar-upload/password-change.

## 7. Reversibility note

Low-risk, additive: `mobile/` is a new isolated Gradle build; no schema migration, no public API change. The two highest-consequence, hardest-to-reverse design points — because both Phase 4 and Phase 5 bind to them — are the `shared` public façade shape (T15) and the `ApiResult`/error-taxonomy design (T2). Everything else is a contained, single-feature change.
