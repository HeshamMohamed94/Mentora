# Mentora — Testing Strategy

Per-platform test types plus the priority E2E flow list, both requested explicitly in the brief.

---

## 1. Backend (Kotlin/Ktor)

| Type | Tooling | Scope |
|---|---|---|
| Unit | JUnit5 + MockK | Service-layer business logic in isolation (e.g. quiz scoring, completion-percentage calculation, publish-readiness validation) — repositories mocked/faked. |
| Integration | Ktor `testApplication` + **Testcontainers (MongoDB module)** | Full route → service → real ephemeral MongoDB instance, per test run — verifies actual query correctness (indexes, transaction behavior) rather than trusting a mock's assumptions about Mongo's behavior. This is deliberately chosen over mocking the database for integration-level tests, since the highest-risk logic in this backend (multi-document transactions, unique-index-based idempotency) is exactly the kind of thing a Mongo mock would let pass while hiding a real bug. |
| API/contract | Ktor `testApplication` | Verifies the error envelope shape, status codes, and auth/RBAC enforcement (a Student token hitting an Instructor-only route gets `403`, etc.) — one focused suite per module, testing the module's public HTTP contract, not its internals. |

**What must be integration-tested, not just unit-tested, because of real correctness risk:** demo-checkout idempotency (§ [`API_CONTRACT.md § 6`](./API_CONTRACT.md) — a duplicate request must never create two enrollments), the lesson-completion → progress → certificate-issuance transaction (§ [`BACKEND_ARCHITECTURE.md § 4`](./BACKEND_ARCHITECTURE.md)), and the quiz `isCorrect`-stripping projection (§ [`DATABASE_MODEL.md § 7`](./DATABASE_MODEL.md) — a regression here is a real security defect, not just a bug).

## 2. KMP Shared Module

`kotlin.test` (or Kotest multiplatform), run against `commonTest` — no platform target needed since `domain`/`usecase` have no platform dependency (§ [`KMP_ARCHITECTURE.md § 5`](./KMP_ARCHITECTURE.md)). One test suite verifies business logic for both Android and iOS at once: use-case orchestration against fake repository implementations, auth/session-refresh state-machine behavior, and the AI Tutor quick-action prompt-construction logic (verifying "Quiz me" never touches anything quiz-module-shaped, per [`AI_TUTOR_ARCHITECTURE.md § 7`](./AI_TUTOR_ARCHITECTURE.md)).

## 3. Android

| Type | Tooling | Scope |
|---|---|---|
| Unit | JUnit + MockK | ViewModel logic (thin — most logic already covered in `shared`'s tests). |
| UI | Compose UI Testing (`createComposeRule`) | Key screens' rendering/interaction: Login/Register form validation feedback, Course Player's Mark Complete/auto-advance, Quiz's answer-selection states, RTL layout smoke test (render a screen under a `LayoutDirection.Rtl` composition local override and assert no visual/interaction regression). |

## 4. iOS

| Type | Tooling | Scope |
|---|---|---|
| Unit | XCTest | Thin ObservableObject logic wrapping shared use cases (again, most logic already covered in `shared`). |
| UI | XCUITest, used lightly | Critical-path smoke tests only (Login, Course Player controls) — not exhaustive per-screen coverage, since SwiftUI preview-based manual verification plus `shared`'s test coverage carries most of the correctness burden for a small team. |

## 5. Web

| Type | Tooling | Scope |
|---|---|---|
| Unit/Component | Vitest + React Testing Library | Individual components (form validation, `CourseCard` content-resilience truncation behavior, error-code-to-message mapping) and hooks (TanStack Query hooks' cache/optimistic-update behavior). |
| E2E | **Playwright** | The priority flows in § 6 below, run against a real (test-seeded) backend + MongoDB in CI — not mocked, since these are the flows the brief explicitly calls "portfolio-priority" and mocking them would defeat the point of an E2E suite. |

Playwright is chosen (over Cypress) for native multi-browser support (Chromium/Firefox/WebKit — WebKit coverage matters for Safari-using reviewers), first-class TypeScript support matching the Next.js codebase, and good CI parallelization out of the box.

## 6. Priority E2E Flows (Web, Playwright)

Every flow named in the brief, mapped to its screens/APIs:

| Flow | Screens exercised | Key assertion |
|---|---|---|
| Register/Login | Register, Login, Dashboard | Successful auth lands on the correct role-appropriate screen; wrong credentials show the generic error (never revealing which field was wrong, per [`AUTH_SECURITY.md § 2`](./AUTH_SECURITY.md)) |
| Explore → Course Details | Explore, Course Details | Search/filter narrows results; tapping a card navigates with correct data |
| Demo Purchase → Enrollment | Course Details, Demo Checkout, Purchase Success | No real-payment field ever renders; completion creates exactly one enrollment (re-running the flow against an already-enrolled course shows "Continue Learning," never a duplicate purchase) |
| Start/Resume Course | Course Player | Resuming lands on the last incomplete lesson at the last known position |
| Complete a Lesson | Course Player | Progress bar updates; auto-advance to next lesson |
| Complete a Quiz | Quiz, Quiz Results | Correct/incorrect breakdown renders with icon+text+color (never color-only — an accessibility-relevant assertion, checked via DOM structure not just visual snapshot); failing routes to Retry, passing routes toward completion |
| Course Completion → Certificate | Quiz Results/Course Player, Certificates List, Certificate Detail | Certificate appears immediately after the completing action, with correct denormalized snapshot data |
| Language switch English ↔ Arabic | Settings (any authenticated screen) | `dir` attribute flips, a known string renders translated, a subsequent page navigation preserves the chosen locale |
| Instructor Course Authoring | Instructor Dashboard, Course Editor (Overview/Curriculum), Lesson Editor, Quiz Editor | Full create → structure → publish path; Publish is blocked with a visible reason until requirements are met (per [`../ux/INSTRUCTOR_ADMIN_UX.md`](../ux/INSTRUCTOR_ADMIN_UX.md)) |
| Admin Course Management | Admin Dashboard, Admin — Manage Courses | Unpublish removes a course from Explore while leaving existing enrollments intact (verified by asserting a previously-enrolled test student still has player access after) |

These ten flows are the CI-blocking Playwright suite — a merge to `main` that breaks any of them fails CI (see [`DEPLOYMENT.md § 5`](./DEPLOYMENT.md)), reflecting their designation as the portfolio's most-demoed, least-acceptable-to-break paths.

## 7. Test Data

A `infra/docker/mongo-init` seed script (also reused by CI's ephemeral Testcontainers/CI-Mongo setup) provisions: a small set of categories, several courses in both `en`/`ar` content languages and a mix of Draft/Published status, at least one course with a quiz and one without, a Learning Path, and one seeded account per role (Student/Instructor/Admin) — this is the same seed data used for local development and CI, so "works in CI" and "works when I run it locally" never silently diverge.

## 8. What's Deliberately Not Built

No load/performance testing infrastructure, no visual-regression screenshot diffing service, and no mutation testing — all reasonable additions at a larger scale, all disproportionate to a portfolio MVP's testing needs per the brief's "avoid overengineering" principle. Noted as plausible future additions, not gaps in this phase's plan.
