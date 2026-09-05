# Mentora — Kotlin Multiplatform Architecture

Sharing boundary decision: [ADR-002](./adr/ADR-002-kmp-sharing-boundary.md). This file details the `shared` module's internal structure and how Android/iOS consume it.

---

## 1. Module Shape

`mobile/shared` (see [`REPOSITORY_STRUCTURE.md § 4`](./REPOSITORY_STRUCTURE.md)) targets Android and iOS (`androidTarget()` + `iosArm64()`/`iosSimulatorArm64()`), producing an Android library (`.aar`, consumed directly by `androidApp` in the same Gradle build) and an iOS framework (`.xcframework`, consumed by `iosApp` via Swift Package Manager, wrapped by SKIE).

```
commonMain/
  domain/          Course, Section, Lesson, Enrollment, Progress, Quiz, Question, QuizAttempt,
                    Certificate, LearningPath, AiConversation, AiMessage, User — plain data classes,
                    kotlinx.serialization-annotated where they cross the wire.
  domain/usecase/   CompleteLessonUseCase, ResumeCourseUseCase, SubmitQuizUseCase,
                    FollowLearningPathUseCase, SendAiTutorMessageUseCase, RegisterUseCase,
                    LoginUseCase, RefreshSessionUseCase, ... — one class per meaningful action,
                    each depending only on repository interfaces (never a concrete implementation).
  data/network/     Ktor Client (multiplatform engine), API DTOs, request/response serializers,
                    an auth interceptor (attaches the bearer token, triggers refresh on 401).
  data/repository/  Interfaces (e.g. CourseRepository) + implementations backed by data/network.
  auth/             SessionManager (orchestrates access/refresh token lifecycle, exposes
                    a StateFlow<AuthState>), TokenStorage (expect/actual interface).
  settings/         PreferenceStore (expect/actual over multiplatform-settings) for language/theme.
androidMain/
  actual TokenStorage → EncryptedSharedPreferences/DataStore-backed implementation.
  actual PreferenceStore → DataStore-backed implementation.
  actual Locale-bridging helpers → java.util.Locale / android.icu.
iosMain/
  actual TokenStorage → Keychain-backed implementation.
  actual PreferenceStore → NSUserDefaults-backed implementation.
  actual Locale-bridging helpers → Foundation.Locale.
```

## 2. Dependency Direction

`domain` depends on nothing platform-specific. `data` implements `domain`'s repository interfaces. `androidApp`/`iosApp` depend on `shared` (specifically on `domain` use cases and `auth.SessionManager`), never the reverse — `shared` has zero awareness that Compose or SwiftUI exist. This is what keeps [ADR-002](./adr/ADR-002-kmp-sharing-boundary.md)'s "UI is never shared" boundary structurally enforced rather than just a convention someone could violate by importing a Compose type into `commonMain`.

## 3. Networking

Ktor Client (multiplatform artifact) configured once in `data/network/`:
- `ContentNegotiation` + `kotlinx.serialization` for JSON.
- An `Auth`-equivalent interceptor plugin: attaches `Authorization: Bearer <accessToken>` from `SessionManager`'s current state; on a `401 AUTH_TOKEN_EXPIRED`, calls `/auth/refresh` once (coalesced — concurrent requests hitting 401 simultaneously trigger exactly one refresh call, not one per request) and retries the original request; on refresh failure, updates `AuthState` to logged-out and lets the UI layer react (navigate to Login).
- Base URL, timeouts, and retry policy for transient network errors (a small number of retries with backoff for idempotent `GET`s only — never retried automatically for a mutating request, to avoid accidental double-submission) are configured centrally here, not per-repository.
- Error responses are deserialized into the same `{ error: { code, ... } }` shape defined in [`API_CONTRACT.md § 3`](./API_CONTRACT.md) and surfaced to use cases as a typed sealed result (`ApiResult.Success<T>` / `ApiResult.Failure(code, ...)`), never a raw exception the UI layer has to string-match.

## 4. Consuming `shared` From Each Platform

**Android:** `androidApp` depends on `:shared` as a normal Gradle module dependency — Kotlin all the way down, no bridging layer needed. A ViewModel calls a use case directly, collects its `Flow`/suspend result, exposes UI state via Compose's `State`/`StateFlow`.

**iOS:** the compiled `shared.xcframework` is imported via Swift Package Manager. **SKIE** is applied to `shared`'s Gradle build (a Gradle plugin, not a separate build step to remember) so the framework's generated Swift interface is idiomatic rather than raw Kotlin/Native interop:

| Kotlin (in `shared`) | Raw K/N→Swift interop (without SKIE) | With SKIE |
|---|---|---|
| `suspend fun login(...)` | Completion-handler closure | `async`/`await` |
| `Flow<Progress>` | A manual `Kotlinx_coroutines_coreFlow` wrapper, no `for await` support | Native Swift `AsyncSequence` — `for try await progress in useCase.observe() { ... }` |
| `sealed class ApiResult<T>` | An open class hierarchy with `is` checks, no exhaustiveness | A real Swift `enum` with associated values, exhaustive `switch` |
| `enum class QuizAnswerState` | An awkward Kotlin-enum bridge | A native Swift `enum` |

Without SKIE, the iOS engineer's daily experience of `shared` would be noticeably non-Swift — exactly the friction that leads teams to "just call the REST API directly from Swift and skip the shared module," which would defeat the entire point of [ADR-002](./adr/ADR-002-kmp-sharing-boundary.md). SKIE is what makes the shared module's Swift-side ergonomics good enough that bypassing it is never the path of least resistance.

## 5. Testing the Shared Module

`commonTest` uses `kotlin.test` (or Kotest multiplatform) to test `domain`/`usecase` logic against a fake/in-memory repository implementation — no platform dependency needed, since this layer has none. This is the single highest-leverage test surface in the mobile codebase: one test suite verifies business logic correctness for both Android and iOS simultaneously. Full detail in [`TESTING_STRATEGY.md § 2`](./TESTING_STRATEGY.md).

## 6. What This Buys, Concretely

- A change to "how is course completion percentage calculated on the client for optimistic UI" is one PR to `shared/domain`, verified once, correct on both platforms simultaneously.
- The AI Tutor's five quick-action definitions ([`../product/PRODUCT_SPEC.md § 11`](../product/PRODUCT_SPEC.md)) are defined once in `shared/domain`, so "what does 'Explain this lesson' actually send to the backend" cannot silently diverge between Android and iOS.
- Auth/session/refresh-token logic — the highest-consequence-if-wrong client logic in the app — is written and tested exactly once.
