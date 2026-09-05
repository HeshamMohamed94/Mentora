# Mentora Technical Architecture

**Status:** Technical Architecture — **APPROVED and LOCKED** (2026-09-04; both open decisions — Web framework, AI provider — resolved by explicit product-owner approval; see [`ADR_INDEX.md`](./ADR_INDEX.md)). Locked product ([`../product/`](../product/)), UX ([`../ux/`](../ux/)), and Design System v1.3.2 ([`../design-system/`](../design-system/)) are inputs, not open questions — nothing in those directories was modified to produce this architecture. No production application code has been written in this phase.

**LOCKED — the Mentora MVP is a local portfolio/demo system** ([ADR-012](./adr/ADR-012-local-demo-scope.md)), not a system deployed to production during the current implementation phase. Every client (Web, Android, iOS) and the Ktor backend run locally — `localhost`, a local MongoDB Community Edition instance, and local filesystem media storage. Cloud hosting (Railway, Fly.io, Vercel, MongoDB Atlas, Cloudflare R2, a CDN) and app-store distribution (Play Store, TestFlight, App Store, Apple Developer Program) are **not** part of the MVP implementation — they appear in this directory only as clearly labeled, optional future evolution.

**Scope:** this directory defines the implementation-ready technical blueprint for Mentora's 29 MVP screens across Web, Android, and iOS, on one shared Kotlin/Ktor backend (started locally by the developer) and one local MongoDB data store — detailed enough that an implementer (human or AI) can execute a milestone from [`IMPLEMENTATION_ROADMAP.md`](./IMPLEMENTATION_ROADMAP.md) without making architectural decisions of its own.

---

## 0. How This Directory Is Organized

| File | Answers |
|---|---|
| [`ARCHITECTURE.md`](./ARCHITECTURE.md) | This file — the map. |
| [`TECH_STACK.md`](./TECH_STACK.md) | The full technology stack, decision-by-decision, with alternatives considered. |
| [`REPOSITORY_STRUCTURE.md`](./REPOSITORY_STRUCTURE.md) | Monorepo layout, module names, directory tree. |
| [`BACKEND_ARCHITECTURE.md`](./BACKEND_ARCHITECTURE.md) | Ktor modular-monolith structure, module boundaries, request lifecycle. |
| [`DATABASE_MODEL.md`](./DATABASE_MODEL.md) | MongoDB collections, relationships, embedding vs. referencing, indexes, lifecycle. |
| [`API_CONTRACT.md`](./API_CONTRACT.md) | REST conventions, versioning, pagination, error envelope, endpoint inventory. |
| [`AUTH_SECURITY.md`](./AUTH_SECURITY.md) | Auth flows, token strategy, RBAC, and the full security requirement set. |
| [`KMP_ARCHITECTURE.md`](./KMP_ARCHITECTURE.md) | What's shared between Android/iOS, what isn't, and why. |
| [`WEB_ARCHITECTURE.md`](./WEB_ARCHITECTURE.md) | The Web technology decision and its rationale (explicitly evaluated, not assumed). |
| [`AI_TUTOR_ARCHITECTURE.md`](./AI_TUTOR_ARCHITECTURE.md) | AI Tutor's client→backend→provider flow, without implementing it. |
| [`MEDIA_ARCHITECTURE.md`](./MEDIA_ARCHITECTURE.md) | Local-filesystem video/thumbnail/upload architecture — never binary-in-MongoDB. |
| [`LOCALIZATION_ARCHITECTURE.md`](./LOCALIZATION_ARCHITECTURE.md) | How English/Arabic + RTL are implemented per platform. |
| [`TESTING_STRATEGY.md`](./TESTING_STRATEGY.md) | Test types and priority E2E flows, per platform. |
| [`DEPLOYMENT.md`](./DEPLOYMENT.md) | Local Development / Local Demo — the two current MVP environments, local networking strategy, and the optional future production-evolution path (clearly labeled, not built). |
| [`IMPLEMENTATION_ROADMAP.md`](./IMPLEMENTATION_ROADMAP.md) | Milestones, dependencies, acceptance criteria, Claude-vs-Codex assignment. |
| [`ADR_INDEX.md`](./ADR_INDEX.md) | Index of [`adr/`](./adr/) — the locked decisions and their reasoning, including [ADR-012](./adr/ADR-012-local-demo-scope.md)'s local-only MVP scope. |

---

## 1. Product Grounding (what this architecture must serve)

Recorded here once so every other document can reference it instead of re-deriving it:

- **29 MVP screens** ([`../product/SCREEN_INVENTORY.md`](../product/SCREEN_INVENTORY.md)), **27 base user flows + 1 localization flow** ([`../product/USER_FLOWS.md`](../product/USER_FLOWS.md)) — no new screens introduced by this architecture phase.
- **Four roles, one role per account:** Guest, Student, Instructor, Admin ([`../product/USER_ROLES.md`](../product/USER_ROLES.md)). Instructor and Admin are Web-only by product decision.
- **Three clients, one backend, one data model:** Web, Android, iOS all read/write the same enrollment/progress/certificate truth ([`../product/PRODUCT_SPEC.md § 10`](../product/PRODUCT_SPEC.md)).
- **Payment is simulated only** — no gateway, no financial data, ever ([`../product/DEMO_PAYMENT_FLOW.md`](../product/DEMO_PAYMENT_FLOW.md)). This architecture never introduces a code path capable of a real charge.
- **English + Arabic are both locked, functional MVP languages**, not "RTL readiness" — LTR/RTL, locale-aware formatting, and a working language switch are required end-to-end on all six surfaces (Public Web, Student Web, Android, iOS, Instructor Web, Admin Web) ([`../product/PRODUCT_SPEC.md § 16`](../product/PRODUCT_SPEC.md)).
- **Course content language ≠ UI language** — this is a metadata field on `Course`, never a translation feature.
- **Mentora Design System v1.3.2 is the only source of visual truth** — Primitive → Semantic → Component token architecture, logical (start/end) layout, WCAG 2.1 AA. This architecture's job is to get those tokens and component contracts into three codebases without three teams inventing three different implementations.
- **Portfolio/demo priority is a real constraint** ([`../product/PRODUCT_SPEC.md § 3, § 15`](../product/PRODUCT_SPEC.md)): Discovery → Course Details → Demo Checkout → Purchase Success → Course Player → Progress → Quiz → AI Tutor → Certificate must be the most polished, most reliable path through the system, and every architecture decision below is judged partly on whether it keeps that path simple and fast to build correctly.

## 2. System Shape, One Paragraph

One **Ktor modular-monolith backend** (JVM/Kotlin), started locally by the developer, fronts a single **local MongoDB Community Edition** database and is the sole source of truth for identity, courses, enrollment, progress, quizzes, certificates, learning paths, and AI Tutor conversations. Three clients, all run locally, consume the same versioned REST API: a **Next.js (React/TypeScript)** website (public SEO surface + authenticated Student/Instructor/Admin app), and native **Android (Jetpack Compose)** / **iOS (SwiftUI)** apps sharing a **Kotlin Multiplatform** module for domain models, networking, and business logic — never UI. Course videos and images live on the **local filesystem**, never in MongoDB; the database holds only their metadata and permission logic — see [ADR-012](./adr/ADR-012-local-demo-scope.md) for the locked local-only MVP scope this entire shape assumes. Design tokens are generated once, by a script, from [`design-system/design-tokens.json`](../design-system/design-tokens.json) into each platform's native styling primitives (CSS custom properties, Compose theme, SwiftUI theme) — never hand-duplicated. See [§ 3](#3-the-shape-in-one-diagram) for the diagram and [`TECH_STACK.md`](./TECH_STACK.md) for why each piece was chosen.

## 3. The Shape, In One Diagram

```
                        ┌─────────────────────────────┐
                        │   design-system/ (LOCKED)    │
                        │   design-tokens.json          │
                        └──────────────┬───────────────┘
                                       │ generated by tools/token-pipeline
                    ┌──────────────────┼──────────────────┐
                    ▼                  ▼                  ▼
            web/tokens.css   android MentoraTokens.kt   ios MentoraTokens.swift
                    │                  │                  │
     ┌──────────────▼───┐   ┌──────────▼─────────┐  ┌────▼─────────────┐
     │   web/            │   │  mobile/androidApp  │  │  mobile/iosApp    │
     │   Next.js (React) │   │  Jetpack Compose     │  │  SwiftUI           │
     │   SSR + CSR        │   │                      │  │                    │
     └──────────┬────────┘   └──────────┬───────────┘  └─────────┬─────────┘
                │                        │  consumes               │  consumes
                │                        ▼  (via SKIE)              ▼
                │              ┌───────────────────────────────────────┐
                │              │   mobile/shared (Kotlin Multiplatform)  │
                │              │   domain models · use cases · Ktor      │
                │              │   client · auth state · repositories    │
                │              └───────────────────┬─────────────────────┘
                │                                   │
                │  REST + JSON, versioned /api/v1    │  REST + JSON, versioned /api/v1
                └──────────────────┬─────────────────┘
                                   ▼
                   ┌───────────────────────────────────────┐
                   │   backend/ — Ktor modular monolith      │
                   │   (run locally: localhost:8080)          │
                   │   auth · users · courses · categories   │
                   │   enrollment · progress · quiz           │
                   │   learningPaths · certificates            │
                   │   aiTutor · instructor · admin · media     │
                   └───────┬───────────────────┬───────────────┘
                           │                    │
                           ▼                    ▼
                 ┌──────────────────┐  ┌───────────────────────┐
                 │  MongoDB Community │  │  Local filesystem       │
                 │  (local instance,  │  │  media storage           │
                 │  all app data)     │  │  (video/images)          │
                 └──────────────────┘  └───────────────────────┘
                           │
                           ▼ (server-side only, keys never leave backend)
                 ┌──────────────────────┐
                 │  AI provider (LLM API) │
                 └──────────────────────┘
```

**No cloud infrastructure appears in this diagram** — every box above runs on the developer's local machine (plus the Android emulator/iOS simulator for the mobile clients), per [ADR-012](./adr/ADR-012-local-demo-scope.md). See [`DEPLOYMENT.md § 4a`](./DEPLOYMENT.md) for how each client's `API_BASE_URL` resolves to the local backend depending on whether it's a browser, an Android emulator, or an iOS simulator.

## 4. Cross-Cutting Rules That Bind Every Document Below

These are load-bearing across every architecture doc in this directory, so they're stated once, here, instead of repeated:

1. **The backend is authoritative.** Progress, enrollment, quiz results, certificate eligibility, and role permissions are computed and enforced server-side. No client ever "declares" a state the backend simply trusts (see [`API_CONTRACT.md`](./API_CONTRACT.md), [`AUTH_SECURITY.md`](./AUTH_SECURITY.md)).
2. **No AI provider key, database credential, or object-storage secret ever reaches a client.** Every third-party call happens server-side ([`AI_TUTOR_ARCHITECTURE.md`](./AI_TUTOR_ARCHITECTURE.md), [`MEDIA_ARCHITECTURE.md`](./MEDIA_ARCHITECTURE.md)).
3. **No MongoDB document is allowed to grow unboundedly.** High-write, high-cardinality data (quiz attempts, AI messages, progress events) always lives in its own collection, referenced — never embedded in a document that's read/written on every request ([`DATABASE_MODEL.md`](./DATABASE_MODEL.md)).
4. **UI language and course-content language are independent fields, end to end** — from the `Course.contentLanguage` metadata field in MongoDB to the `Accept-Language`-independent API contract to each client's own i18n layer ([`LOCALIZATION_ARCHITECTURE.md`](./LOCALIZATION_ARCHITECTURE.md)).
5. **Backend error responses carry a machine-readable code, never localized or raw prose.** Each client localizes the code into the active UI language from its own string resources — this is the one place localization and error-handling architecture intersect, and it's resolved once, system-wide ([`API_CONTRACT.md`](./API_CONTRACT.md)).
6. **Every layout primitive in every client uses the Design System's logical (start/end) properties, never physical left/right**, per [`../design-system/DESIGN_RULES.md` rule 16](../design-system/DESIGN_RULES.md) — this is a design rule already, restated here because it has real technical-architecture consequences (Web must use CSS logical properties throughout; Compose/SwiftUI layout code must never hardcode `left`/`right`).
7. **Nothing in this architecture builds a code path capable of a real financial transaction.** `DemoPurchase`/`SimulatedCheckout`/`Enrollment` are the only domain concepts in the purchase flow — no card fields, no gateway client, no fake transaction IDs ([`DEMO_PAYMENT_FLOW.md`](../product/DEMO_PAYMENT_FLOW.md), reflected in [`DATABASE_MODEL.md`](./DATABASE_MODEL.md) and [`API_CONTRACT.md`](./API_CONTRACT.md)).
8. **Simplicity is a requirement, not a fallback.** A modular monolith, one database, REST (not GraphQL), and a monorepo are all deliberate rejections of more "impressive"-sounding architectures that would add operational and cognitive cost without a corresponding MVP need — see each decision's alternative-considered section in [`ADR_INDEX.md`](./ADR_INDEX.md).

## 5. What This Phase Explicitly Did Not Do

- Did not modify any file under `design-system/`, `product/`, or `ux/`.
- Did not write, generate, or scaffold any production application code (backend, web, Android, iOS, or shared KMP).
- Did not invoke or delegate to Codex.
- AI provider selection is resolved: the Anthropic Claude API is approved as the initial `AiProvider` implementation (product-owner sign-off, 2026-09-04) — the AI Tutor architecture remains provider-agnostic by design (see [`AI_TUTOR_ARCHITECTURE.md § 7`](./AI_TUTOR_ARCHITECTURE.md) and [`adr/ADR-009-ai-provider-abstraction.md`](./adr/ADR-009-ai-provider-abstraction.md)); this phase still did not implement any AI Tutor code.
- Did not add, remove, or resize any of the 29 approved MVP screens.
- Did not treat cloud infrastructure as part of the MVP implementation — no cloud account, hosting service, managed database, or object-storage bucket is required, provisioned, or assumed necessary by this architecture ([ADR-012](./adr/ADR-012-local-demo-scope.md)).

See [`IMPLEMENTATION_ROADMAP.md`](./IMPLEMENTATION_ROADMAP.md) for what happens next, and the final report delivered in-conversation for the explicit approval gate before any implementation begins.
