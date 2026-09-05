# ADR-002: Kotlin Multiplatform Sharing Boundary

**Status:** Locked
**Date:** 2026-09-04

## Decision

Android and iOS share exactly one thing: a `shared` Kotlin Multiplatform module containing **domain models, validation, use cases, repository interfaces + implementations, the networking client, and authentication-state management.** UI is never shared — Jetpack Compose (Android) and SwiftUI (iOS) are each hand-built, native, and idiomatic.

## Context

The brief explicitly asked this to be evaluated, not assumed: "Define exactly WHAT should be shared... Define what should remain platform-specific... Do not force UI sharing if native UI gives a better product."

Mentora's own product/UX documentation already answers part of this: [`../ux/MOBILE_UX.md`](../../ux/MOBILE_UX.md) prescribes a Curriculum `BottomSheet` on mobile where Web uses a persistent sidebar — a platform-appropriate structural difference, not a shared-UI target. [`../design-system/DESIGN_RULES.md` rule 10](../../design-system/DESIGN_RULES.md) explicitly sanctions "platform-specific UX differences... where they improve usability," while requiring the *visual language* (tokens) to stay identical — a distinction this ADR maps directly onto the shared/native boundary.

## What Is Shared (`mobile/shared`)

| Area | Why it's safe/valuable to share |
|---|---|
| Domain models (`Course`, `Lesson`, `Enrollment`, `Progress`, `Quiz`, `QuizAttempt`, `Certificate`, `LearningPath`, `AiConversation`, `User`) | Pure data + business meaning, identical on both platforms by definition — this *is* "the same product on three platforms" ([`../product/PRODUCT_SPEC.md § 10`](../../product/PRODUCT_SPEC.md)). |
| Validation (form field rules: password strength, required-field checks) | Logic, not UI — prevents Android and iOS silently drifting on what "a valid password" means. |
| Use cases / interactors (`CompleteLessonUseCase`, `SubmitQuizUseCase`, `FollowLearningPathUseCase`, `SendAiTutorMessageUseCase`) | The orchestration logic (call repository, handle result, map errors) is identical business behavior — writing it twice risks the two platforms' business rules silently diverging over time. |
| Repository interfaces + implementations | The *shape* of "how do I get a course list" and the actual HTTP-calling code are identical; only the ViewModel/ObservableObject consuming a repository is platform-specific. |
| Networking (Ktor Client, DTOs, `kotlinx.serialization`, interceptors for auth headers/refresh) | The wire protocol to the backend is one contract — sharing it means one implementation of retry/refresh/error-mapping logic instead of two that can disagree. |
| Auth-state management (token storage abstraction via `expect`/`actual`, session/refresh orchestration) | The *policy* (when to refresh, when to log out) is shared; the *mechanism* (Keychain vs. EncryptedSharedPreferences) is `expect`/`actual`-platform-specific underneath a shared interface. |
| Progress/quiz/AI-Tutor domain logic (e.g. client-side optimistic-update reconciliation, quick-action definitions) | Same reasoning as use cases — this is business logic that must not diverge between platforms. |

## What Is NOT Shared

| Area | Why it stays platform-specific |
|---|---|
| **All UI** (Compose vs. SwiftUI) | Native UI toolkits give each platform's users a genuinely better, more idiomatic experience (gesture handling, accessibility integration, platform look-and-feel) — forcing a shared UI layer (e.g. Compose Multiplatform UI, or a shared abstraction layer over both) would mean building to the lowest common denominator of two very different toolkits, exactly what rule 10 warns against. |
| **Localized string resources** | Android's `strings.xml`/`values-ar/` and iOS's String Catalogs are each the *idiomatic, tooling-integrated* mechanism for that platform (Xcode's String Catalog editor, Android Studio's translation editor, lint checks for missing translations) — sharing translated strings through KMP would mean neither platform's tooling can validate translation completeness natively. See [`LOCALIZATION_ARCHITECTURE.md`](../LOCALIZATION_ARCHITECTURE.md). |
| **Design tokens as consumed types** (`Color`, `TextStyle`/`Font`) | `androidx.compose.ui.graphics.Color` and SwiftUI's `Color` are different types with no shared representation worth abstracting — see [ADR-011](./ADR-011-design-token-pipeline.md). |
| **Video playback** (ExoPlayer/Media3 vs. AVPlayer) | Each platform's native player is the right tool; `shared` only defines the *interface* (`play`, `pause`, `seek`, `currentPosition: Flow<Duration>`) that each platform's ViewModel wraps around its native player. |
| **File/media pickers, native share sheets** | OS-owned chrome, explicitly excluded from cross-platform abstraction by [`../design-system/DESIGN_SYSTEM.md § 8`](../../design-system/DESIGN_SYSTEM.md)'s icon rule and by nature. |
| **Navigation** (Navigation-Compose vs. `NavigationStack`) | Each platform's own idiomatic navigation stack, driven by the same IA ([`../product/INFORMATION_ARCHITECTURE.md § 3`](../../product/INFORMATION_ARCHITECTURE.md)) but implemented natively. |
| **Local relational persistence** | Not built at all in MVP — see [`TECH_STACK.md § 6`](../TECH_STACK.md). |

## Options Considered

### Option A — Domain/data/network shared, UI native (chosen)
Described above.

### Option B — Maximal sharing via Compose Multiplatform (shared UI too)
**Why not:** the product's own UX spec already diverges Android/iOS-vs-Web-shaped layouts by platform category (mobile bottom sheet vs. web sidebar) but treats Android and iOS as identical in UX terms — so in principle Android/iOS UI *could* be shared via Compose Multiplatform's iOS target. Rejected anyway: Compose Multiplatform's iOS rendering (Skia-based, not native UIKit/SwiftUI views) is a materially less mature, less "feels native" solution in 2026 than SwiftUI, with weaker VoiceOver/Dynamic-Type/keyboard-avoidance integration — exactly the accessibility bar [`../design-system/ACCESSIBILITY.md`](../../design-system/ACCESSIBILITY.md) sets as a floor, not a nice-to-have. It would also mean the portfolio's iOS app isn't "real SwiftUI," undercutting a stated project goal (demonstrating credible, idiomatic engineering per platform).

### Option C — No sharing at all (two fully independent native codebases)
**Why not:** guarantees the two platforms' business logic (progress calculation, quiz scoring interpretation, AI Tutor quick-action definitions) drifts over time, directly undermining [`../product/PRODUCT_SPEC.md § 10`](../../product/PRODUCT_SPEC.md)'s "same product on three platforms" requirement, and duplicates real engineering effort (networking, auth, retry/refresh logic) for zero product benefit.

## Consequences

- `mobile/shared` has no UI-framework dependency at all — it's pure Kotlin (`commonMain`) plus `expect`/`actual` platform bridges for storage/secure-storage only.
- Android ViewModels and iOS `ObservableObject`s are both thin adapters over the same `shared` use cases/repositories — this is where SKIE ([`TECH_STACK.md § 5`](../TECH_STACK.md)) earns its keep on the iOS side.
- Adding a feature (e.g. a new AI Tutor quick action) means one PR to `shared` plus two thin UI PRs — not two independent business-logic implementations.

## Migration Path

If a genuine offline mode is ever built (Post-MVP), `shared` gains a SQLDelight-backed local cache underneath the existing repository interfaces — no consuming ViewModel/UI code needs to change, since they already depend on the repository *interface*, not its implementation.
