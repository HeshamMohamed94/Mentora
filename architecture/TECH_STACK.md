# Mentora — Technology Stack

Every decision below follows the same shape: **Decision → Why → Strongest alternative → Why not → Migration path.** Full write-ups for the highest-stakes decisions are promoted to an ADR in [`adr/`](./adr/) (linked inline); everything else is resolved here at the same rigor, just without a standalone file.

---

## 1. Backend Language & Framework — Kotlin + Ktor

**Decision:** Kotlin on the JVM, Ktor as the HTTP server framework.

**Why:** Kotlin gives one language across backend and the KMP-shared mobile layer (shared DTOs/serialization models can be literally the same code, not a re-implementation per platform — see [`KMP_ARCHITECTURE.md`](./KMP_ARCHITECTURE.md)). Ktor is lightweight, coroutine-native (fits Mongo's coroutine driver and an AI provider's streaming responses naturally), and doesn't impose a heavyweight framework's opinions (no XML config, no reflection-magic DI unless you opt in) — appropriate for a modular monolith sized for a small team. Coroutines make the AI Tutor's token-streaming response and Mongo's async I/O compose naturally without callback/reactive-stream ceremony.

**Strongest alternative:** Spring Boot (Kotlin). Spring's ecosystem (Spring Security, Spring Data MongoDB, Spring Data REST) would reduce some boilerplate and is more familiar to more backend engineers.

**Why not:** Spring's conventions (annotation-driven DI, AOP, auto-configuration magic) add a learning-curve and "what is actually happening" opacity that works against this project's stated goal of being an explicit, inspectable, portfolio-quality architecture — a reviewer reading Ktor route definitions sees the actual request handling; a reviewer reading Spring sees annotations that defer to framework magic. Ktor is also lighter to run and deploy (smaller memory footprint, faster cold start) which matters for a cost-conscious portfolio deployment.

**Migration path:** Ktor's plugin model is close enough to Spring's filter/interceptor model that migrating a module at a time (if the team ever grew and wanted Spring's ecosystem) is realistic; this is a low-regret choice either way. **Locked. This was the direction proposed for evaluation and is confirmed appropriate — see [`adr/ADR-003-backend-architecture-style.md`](./adr/ADR-003-backend-architecture-style.md) for the monolith-vs-microservices half of this decision.**

---

## 2. Database — MongoDB (local Community Edition for the MVP)

**Decision:** MongoDB. **For the current MVP, this is MongoDB Community Edition running locally on the development machine** — the active MVP database. MongoDB Atlas is not used in MVP; it's retained only as a documented, optional future migration path (§ [ADR-004](./adr/ADR-004-database-engine.md#migration-path), per [ADR-012](./adr/ADR-012-local-demo-scope.md)).

**Why, and why not PostgreSQL (the strongest alternative):** see [`adr/ADR-004-database-engine.md`](./adr/ADR-004-database-engine.md) for the full analysis. Short version: Mentora's core content object (a `Course` with ordered `Section`s and `Lesson`s) is naturally hierarchical and always authored/read as a whole — a natural document fit. MongoDB's official Kotlin Coroutine driver is mature and idiomatic with Ktor's coroutine model. The risk PostgreSQL would have solved better — relational integrity across `Enrollment`/`Progress`/`QuizAttempt` — is mitigated by MongoDB's multi-document ACID transactions (available since 4.0) at the specific points that need them (checkout → enrollment, lesson-complete → progress → certificate-eligibility), and by disciplined reference-vs-embed modeling documented per-collection in [`DATABASE_MODEL.md`](./DATABASE_MODEL.md).

**Driver:** the official **MongoDB Kotlin Coroutine Driver** (`org.mongodb:mongodb-driver-kotlin-coroutine`), not KMongo. KMongo is community-maintained and has slowed in active development; the official driver now ships first-class coroutine support and integrates with `kotlinx.serialization`-style codecs, which removes KMongo's original reason to exist.

---

## 3. Web Frontend — Next.js (React + TypeScript)

**Decision:** Next.js 14+ (App Router), React, TypeScript.

Full evaluation (including why Kotlin Multiplatform is explicitly **not** used to share Web UI) is in [`WEB_ARCHITECTURE.md`](./WEB_ARCHITECTURE.md) and [`adr/ADR-001-web-frontend-framework.md`](./adr/ADR-001-web-frontend-framework.md). Summary: Next.js is the only evaluated option that satisfies the public site's real SEO requirement (SSR/SSG for Landing, Explore, Course Details, Learning Paths) while also being a perfectly capable SPA-grade framework for the authenticated app, Course Player, and AI Tutor — without needing two different frontend stacks for one Website.

**Styling:** Tailwind CSS v4, configured so every Tailwind utility resolves to a **generated CSS custom property** from `design-tokens.json` (via `@theme` referencing `var(--color-brand-primary)` etc.) — never Tailwind's own default palette/spacing scale. This keeps a single source of truth (the token JSON) while giving normal Tailwind developer velocity. RTL uses Tailwind's logical-property utilities (`ps-4`/`pe-4`/`text-start`, never `pl-4`/`pr-4`/`text-left`), consistent with [`../design-system/DESIGN_RULES.md` rule 16](../design-system/DESIGN_RULES.md).

**Data/state:** TanStack Query (React Query) for all server state (caching, refetch, optimistic updates for e.g. "Mark Complete"); React Hook Form + Zod for form state/validation (client-side validation mirrors, but never replaces, backend validation); a small amount of client-only UI state (Sidebar collapsed, active theme override) in React Context — no Redux/MobX; the app's state needs don't justify one.

**i18n:** `next-intl`, ICU message catalogs (`en.json`/`ar.json`), locale-prefixed routes (`/en/...`, `/ar/...`). See [`LOCALIZATION_ARCHITECTURE.md`](./LOCALIZATION_ARCHITECTURE.md).

---

## 4. Android — Kotlin + Jetpack Compose

**Decision:** Jetpack Compose (Material 3 primitives skinned entirely by Mentora's generated theme, never M3's default look), Kotlin, single-Activity + Navigation-Compose.

This was the proposed direction and is confirmed appropriate without reservation: Compose is Google's current recommended UI toolkit, has first-class Kotlin/coroutine integration (matching the shared KMP layer), and [`../design-system/platform-mapping.md`](../design-system/platform-mapping.md) already specifies Android token consumption in Compose terms (`MentoraTheme.colors.*`, `CompositionLocal`) — there is no real alternative to evaluate here (a View-system/XML UI would be strictly worse for a new 2026 codebase).

**DI:** Koin (not Hilt). Koin has no annotation-processing/KSP step, works identically in the KMP `shared` module and in `androidApp` (one DI story across both, rather than Hilt for Android + a separate manual-wiring approach in `shared`), and is simple enough not to fight a small codebase.

**Navigation:** Navigation-Compose, one `NavHost` per bottom-nav tab's independent back stack, per [`../product/INFORMATION_ARCHITECTURE.md § 3`](../product/INFORMATION_ARCHITECTURE.md).

---

## 5. iOS — Swift + SwiftUI

**Decision:** SwiftUI, Swift, `NavigationStack` per tab.

Confirmed appropriate for the same reason as Compose: it's Apple's current recommended toolkit, has mature Dynamic Type / VoiceOver / RTL support (all required by [`../design-system/ACCESSIBILITY.md`](../design-system/ACCESSIBILITY.md) and [`LOCALIZATION.md`](../design-system/LOCALIZATION.md)), and `platform-mapping.md` already specifies iOS token consumption in SwiftUI terms.

**Consuming the shared module:** the `shared` KMP module compiles to an `.xcframework`, imported via Swift Package Manager (not CocoaPods — SPM is Apple's native, dependency-free path and Kotlin/Native's Gradle plugin can emit an SPM-consumable package directly). **SKIE** (Swift Kotlin Interface Enhancer) wraps the raw Kotlin/Native-to-Swift bridge to produce idiomatic Swift: `suspend fun` → `async`/`await`, Kotlin `Flow` → Swift `AsyncSequence`, Kotlin sealed classes/enums (e.g. `ApiResult`, `QuizAnswerState`) → real Swift `enum`s with exhaustive `switch`. Without SKIE, the raw interop produces callback-based, non-exhaustive, distinctly non-Swift APIs that would make the shared module unpleasant enough to use that iOS engineers would be tempted to bypass it — SKIE removes that temptation. See [`KMP_ARCHITECTURE.md § 4`](./KMP_ARCHITECTURE.md).

---

## 6. Kotlin Multiplatform — Shared Client Layer

**Decision:** one `shared` KMP module (Android + iOS targets) containing domain models, use cases, repository interfaces + implementations, the Ktor Client networking layer, and auth-state management. **UI is never shared** — Compose and SwiftUI are each hand-built per platform, per [`../design-system/DESIGN_RULES.md` rule 10](../design-system/DESIGN_RULES.md) ("platform-specific UX differences are allowed where they improve usability") and the product's own mobile UX spec, which already prescribes platform-idiomatic patterns (Curriculum as a `BottomSheet` on mobile vs. a persistent sidebar on Web).

Full boundary definition (what's shared, what's platform-specific, and why) is in [`KMP_ARCHITECTURE.md`](./KMP_ARCHITECTURE.md) and [`adr/ADR-002-kmp-sharing-boundary.md`](./adr/ADR-002-kmp-sharing-boundary.md).

**Networking:** Ktor Client (multiplatform) with `kotlinx.serialization` for JSON — the same serialization annotations can describe request/response DTOs shared with the backend's own Ktor server code (not the same module, but the same shape, reducing drift).

**Local persistence (lightweight only):** `multiplatform-settings` (wraps `EncryptedSharedPreferences`/DataStore on Android, Keychain/`UserDefaults` on iOS) for auth tokens and the language/theme preference. **No SQLDelight, no local relational cache in MVP** — Mentora's MVP explicitly excludes offline downloads ([`../product/MVP_SCOPE.md § 2`](../product/MVP_SCOPE.md)), so a full local database is unjustified complexity; a future offline mode is the natural trigger to add SQLDelight to `shared`, not before.

---

## 7. Authentication & Authorization

**Decision:** email+password only (no SSO, no password reset — both Post-MVP), BCrypt password hashing, short-lived JWT access tokens + rotating opaque refresh tokens, httpOnly cookies for Web / secure platform storage for mobile. Full detail in [`AUTH_SECURITY.md`](./AUTH_SECURITY.md) and [`adr/ADR-006-authentication-strategy.md`](./adr/ADR-006-authentication-strategy.md).

---

## 8. API Style — REST

**Decision:** versioned REST (`/api/v1/...`), JSON, cursor-based pagination, a fixed error-code contract. Full detail and the GraphQL alternative in [`API_CONTRACT.md`](./API_CONTRACT.md) and [`adr/ADR-007-api-style.md`](./adr/ADR-007-api-style.md).

---

## 9. Media & Local Storage

**Decision:** local filesystem storage for the MVP, behind a `MediaStorage` abstraction, served through controlled Ktor endpoints — never a byte of video/image data in MongoDB, never a bare static-file mount with no access control. S3-compatible object storage (Cloudflare R2) behind a CDN is retained only as a documented, optional future migration path — a second `MediaStorage` implementation, not a redesign. Full detail in [`MEDIA_ARCHITECTURE.md`](./MEDIA_ARCHITECTURE.md) and [`adr/ADR-008-media-storage.md`](./adr/ADR-008-media-storage.md).

---

## 10. AI Tutor Provider

**Decision:** a provider-agnostic `AiProvider` interface behind the backend's `aiTutor` module — the specific LLM API is the **Anthropic Claude API**, approved by explicit product-owner sign-off (2026-09-04). The API key is backend-only configuration, never exposed to any client, and no permanent Claude model identifier is hardcoded — the model name is read from backend configuration, so the abstraction still allows a future provider/model change without redesigning clients or domain architecture. See [`AI_TUTOR_ARCHITECTURE.md`](./AI_TUTOR_ARCHITECTURE.md) and [`adr/ADR-009-ai-provider-abstraction.md`](./adr/ADR-009-ai-provider-abstraction.md).

---

## 11. Design Token Pipeline

**Decision:** Style Dictionary, generating platform-native token files from `design-system/design-tokens.json` — `web/tokens.css` (CSS custom properties), `androidApp/.../MentoraTokens.kt` (Compose `Color`/`TextStyle` objects), `iosApp/.../MentoraTokens.swift` (SwiftUI `Color`/`Font` values). Tokens are **not** part of the KMP `shared` module, because `Color`/`TextStyle` are UI-framework types that don't port between Compose and SwiftUI — sharing them at the KMP layer would mean immediately unwrapping them back into platform types anyway. Full detail in [`adr/ADR-011-design-token-pipeline.md`](./adr/ADR-011-design-token-pipeline.md). This is exactly the mechanism [`../design-system/platform-mapping.md § 9`](../design-system/platform-mapping.md) already names ("via a token-transform script, e.g. Style Dictionary") — this decision formalizes it, it doesn't invent it.

---

## 12. Repository Strategy — Monorepo

**Decision:** one repository. Full detail in [`REPOSITORY_STRUCTURE.md`](./REPOSITORY_STRUCTURE.md) and [`adr/ADR-010-repository-strategy.md`](./adr/ADR-010-repository-strategy.md).

---

## 13. Testing

**Decision:** JUnit5 + MockK + a real local MongoDB instance (backend, not Testcontainers), kotlin.test (KMP shared), Compose UI testing (Android), XCTest (iOS), Vitest/React Testing Library + Playwright (Web). Full detail in [`TESTING_STRATEGY.md`](./TESTING_STRATEGY.md).

---

## 14. Deployment Targets

**Decision (current MVP):** local only, per [ADR-012](./adr/ADR-012-local-demo-scope.md) — the Ktor backend, MongoDB Community Edition, and local filesystem media storage all run on the developer's machine; Web/Android/iOS clients run and are demoed locally (dev server / emulator / simulator). No cloud hosting service (Railway, Fly.io, MongoDB Atlas, Vercel, Cloudflare R2) and no app-store distribution (Play Store, TestFlight, App Store) is part of MVP scope. Full detail, local networking/config strategy, and the optional future production-evolution path (clearly labeled, not built) in [`DEPLOYMENT.md`](./DEPLOYMENT.md).

---

## 15. Observability

**Decision:** structured JSON logging (Logback + `logstash-logback-encoder`), a request-correlation-ID plugin (Ktor `CallId`), a `/healthz` endpoint checking Mongo connectivity. No metrics/tracing stack (Prometheus/Grafana/APM) in MVP — noted as a documented future-evolution step in [`DEPLOYMENT.md`](./DEPLOYMENT.md), not built now, per the explicit instruction to avoid enterprise observability infrastructure without need.

---

## 16. Summary Table

| Layer | Choice | Alternative considered | ADR |
|---|---|---|---|
| Backend language/framework | Kotlin + Ktor, run locally | Spring Boot (Kotlin) | — (§ 1 above) |
| Database | MongoDB, local Community Edition (MVP) | PostgreSQL | [ADR-004](./adr/ADR-004-database-engine.md) |
| Mongo driver | Official Kotlin Coroutine driver | KMongo | — (§ 2 above) |
| Backend architecture style | Modular monolith | Microservices | [ADR-003](./adr/ADR-003-backend-architecture-style.md) |
| Web framework | Next.js (React/TS) | Compose Multiplatform for Web / plain Vite SPA | [ADR-001](./adr/ADR-001-web-frontend-framework.md) |
| Web styling | Tailwind v4 on generated CSS vars | Hand-written CSS Modules | — (§ 3 above) |
| Web state | TanStack Query + RHF/Zod | Redux Toolkit | — (§ 3 above) |
| Android UI | Jetpack Compose | Views/XML | — (§ 4 above, no real alternative) |
| iOS UI | SwiftUI | UIKit | — (§ 5 above, no real alternative) |
| Mobile DI | Koin | Hilt (Android-only) | — (§ 4 above) |
| Shared client layer | KMP (`shared` module) | Two independent native codebases | [ADR-002](./adr/ADR-002-kmp-sharing-boundary.md) |
| iOS interop | SKIE | Raw Kotlin/Native interop | — (§ 5 above) |
| Auth | JWT + rotating refresh, BCrypt | Session-only / localStorage JWT | [ADR-006](./adr/ADR-006-authentication-strategy.md) |
| API style | REST | GraphQL | [ADR-007](./adr/ADR-007-api-style.md) |
| Media storage | Local filesystem (MVP), behind `MediaStorage` abstraction | S3-compatible (R2) + presigned URLs (optional future evolution) | [ADR-008](./adr/ADR-008-media-storage.md) |
| AI provider | Abstracted interface, initial provider: Anthropic Claude API | Direct client→provider calls | [ADR-009](./adr/ADR-009-ai-provider-abstraction.md) |
| Token pipeline | Style Dictionary | Hand-maintained per-platform tokens | [ADR-011](./adr/ADR-011-design-token-pipeline.md) |
| Repository | Monorepo | Multi-repo | [ADR-010](./adr/ADR-010-repository-strategy.md) |
| Backend host (MVP) | Local machine (`localhost`) | Railway / Fly.io (optional future evolution) | [ADR-012](./adr/ADR-012-local-demo-scope.md) ([`DEPLOYMENT.md`](./DEPLOYMENT.md)) |
| DB host (MVP) | Local machine (MongoDB Community) | MongoDB Atlas (optional future evolution) | [ADR-012](./adr/ADR-012-local-demo-scope.md) ([`DEPLOYMENT.md`](./DEPLOYMENT.md)) |
| Web host (MVP) | Local dev server (`next dev`) | Vercel (optional future evolution) | [ADR-012](./adr/ADR-012-local-demo-scope.md) ([`DEPLOYMENT.md`](./DEPLOYMENT.md)) |
