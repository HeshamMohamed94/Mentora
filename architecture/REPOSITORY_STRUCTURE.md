# Mentora — Repository Structure

Monorepo, per [ADR-010](./adr/ADR-010-repository-strategy.md). This file is the concrete directory tree and module-naming convention every implementer (Claude or Codex) follows — no milestone invents its own top-level directory without updating this file first.

---

## 1. Top-Level Tree

```
Mentora/
├── design-system/              [LOCKED — do not modify]
├── product/                    [LOCKED — do not modify]
├── ux/                         [LOCKED — do not modify]
├── architecture/               [this deliverable]
│   └── adr/
│
├── backend/                    Kotlin + Ktor modular monolith
├── web/                        Next.js (React/TypeScript)
├── mobile/
│   ├── shared/                 Kotlin Multiplatform (Android + iOS targets)
│   ├── androidApp/             Jetpack Compose
│   └── iosApp/                 SwiftUI (Xcode project/workspace)
├── tools/
│   └── token-pipeline/         Style Dictionary config + generation script
├── infra/
│   ├── docker/                 docker-compose for local MongoDB (optional convenience — see DEPLOYMENT.md § 1)
│   └── ci/                     shared CI configuration fragments (if any beyond .github/workflows)
├── .github/
│   └── workflows/              GitHub Actions CI/CD (see DEPLOYMENT.md § 5)
└── README.md                   Repo-level orientation (points here and to product/ux/design-system)
```

---

## 2. `backend/` — Ktor Modular Monolith

```
backend/
├── build.gradle.kts
├── settings.gradle.kts
├── src/
│   ├── main/
│   │   ├── kotlin/com/mentora/backend/
│   │   │   ├── Application.kt              # entry point, module wiring
│   │   │   ├── plugins/                    # Ktor plugin configuration (Auth, CORS, StatusPages, Serialization, CallId, RateLimit, RequestValidation)
│   │   │   ├── config/                     # typed config loading (env vars/secrets)
│   │   │   ├── common/                     # shared DTOs, error types, pagination helpers, Mongo codecs
│   │   │   ├── auth/                       # registration, login, logout, token issuance/rotation
│   │   │   ├── users/                      # user profile, role assignment
│   │   │   ├── courses/                    # course CRUD, sections, lessons
│   │   │   ├── categories/                 # category CRUD
│   │   │   ├── enrollment/                 # demo checkout, enrollment records
│   │   │   ├── progress/                   # lesson/course progress, resume position
│   │   │   ├── quiz/                       # quiz authoring, quiz-taking, attempts, scoring
│   │   │   ├── learningpaths/              # learning path CRUD, follow/unfollow
│   │   │   ├── certificates/               # certificate issuance/retrieval
│   │   │   ├── aitutor/                    # conversation/message persistence, AiProvider interface + impl, prompt construction
│   │   │   ├── instructor/                 # instructor-scoped aggregation endpoints (dashboard stats)
│   │   │   ├── admin/                      # admin-scoped endpoints (platform overview, moderation)
│   │   │   └── media/                      # MediaStorage abstraction + local-filesystem impl, media metadata, signed playback URLs (see MEDIA_ARCHITECTURE.md)
│   │   └── resources/
│   │       └── application.conf            # Ktor HOCON config (per-environment overrides via env vars)
│   └── test/
│       └── kotlin/com/mentora/backend/     # mirrors main/ package structure — see TESTING_STRATEGY.md
├── storage/
│   └── media/                              # MEDIA_STORAGE_ROOT — local filesystem media storage, gitignored (see MEDIA_ARCHITECTURE.md § 1, § 6)
├── Dockerfile                              # optional; not required to run the MVP locally, see DEPLOYMENT.md § 1
└── docker-compose.override.yml             # backend-specific local overrides (optional)
```

Each feature package (`auth`, `courses`, ...) internally follows the same three-layer shape — `routes` (Ktor route definitions) → `service` (business logic, transaction boundaries) → `repository` (Mongo collection access) — detailed in [`BACKEND_ARCHITECTURE.md § 2`](./BACKEND_ARCHITECTURE.md).

---

## 3. `web/` — Next.js

```
web/
├── package.json
├── next.config.ts
├── tailwind.config.ts                      # extends theme from generated tokens.css custom properties
├── messages/
│   ├── en.json                             # next-intl message catalog
│   └── ar.json
├── styles/
│   └── tokens.css                          # GENERATED — do not hand-edit (tools/token-pipeline output)
├── src/
│   ├── app/
│   │   ├── [locale]/
│   │   │   ├── (public)/                   # Landing, Explore, Course Details, Learning Paths, Login, Register — SSR/SSG
│   │   │   ├── (app)/                      # authenticated Student surface — Dashboard, My Learning, Course Player, Quiz, AI Tutor, Certificates, Profile, Settings
│   │   │   ├── (instructor)/               # Instructor Web
│   │   │   └── (admin)/                    # Admin Web
│   │   └── layout.tsx                      # root layout: <html dir="ltr|rtl" lang="en|ar">
│   ├── components/                         # shared component library, one component = one COMPONENTS.md entry
│   ├── lib/
│   │   ├── api/                            # typed API client (fetch wrapper, TanStack Query hooks per domain)
│   │   ├── auth/                           # session/cookie helpers
│   │   └── i18n/                           # locale detection/formatting helpers (Intl wrappers forcing Western numerals)
│   └── middleware.ts                       # locale detection/redirect, auth-gate for (app)/(instructor)/(admin)
└── e2e/                                     # Playwright specs — see TESTING_STRATEGY.md
```

---

## 4. `mobile/` — KMP + Native UI

```
mobile/
├── settings.gradle.kts                     # includes :shared and :androidApp
├── shared/
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/com/mentora/shared/
│       │   ├── domain/                     # Course, Lesson, Enrollment, Progress, Quiz, Certificate, LearningPath, AiConversation, User models + use cases
│       │   ├── data/
│       │   │   ├── network/                # Ktor Client setup, DTOs, kotlinx.serialization
│       │   │   └── repository/             # repository interfaces + implementations
│       │   ├── auth/                       # session/token orchestration (expect/actual storage boundary)
│       │   └── settings/                   # multiplatform-settings wrapper (language/theme prefs)
│       ├── androidMain/kotlin/.../          # actual implementations: secure storage, platform Locale bridging
│       └── iosMain/kotlin/.../              # actual implementations: Keychain, Foundation Locale bridging
├── androidApp/
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/mentora/android/
│       ├── MainActivity.kt
│       ├── theme/                          # MentoraTokens.kt (GENERATED) + MentoraTheme.kt (CompositionLocal wiring)
│       ├── navigation/                     # NavHost + per-tab back stacks
│       ├── ui/                             # Composables, one per COMPONENTS.md entry, organized by feature package (courses/, player/, quiz/, aitutor/, ...)
│       └── res/values(-ar)/strings.xml      # localized strings
└── iosApp/
    ├── iosApp.xcodeproj / .xcworkspace
    ├── iosApp/
    │   ├── MentoraApp.swift
    │   ├── Theme/                          # MentoraTokens.swift (GENERATED) + MentoraColors.xcassets
    │   ├── Navigation/                      # NavigationStack per tab
    │   ├── Features/                        # SwiftUI views, organized the same way as androidApp/ui/
    │   └── Resources/
    │       └── Localizable.xcstrings        # String Catalog (en/ar)
    └── Podfile / Package.swift              # SPM import of shared.xcframework (see KMP_ARCHITECTURE.md)
```

---

## 5. `tools/token-pipeline/`

```
tools/token-pipeline/
├── package.json
├── style-dictionary.config.js
└── transforms/                             # custom Style Dictionary transforms/formats per platform (see ADR-011)
```

Reads `design-system/design-tokens.json` + `design-system/themes/*.json`; writes into `web/styles/tokens.css`, `mobile/androidApp/.../theme/MentoraTokens.kt`, `mobile/iosApp/.../Theme/MentoraTokens.swift`. Run via `npm run generate` locally and as a CI check (see [`DEPLOYMENT.md § 5`](./DEPLOYMENT.md)).

---

## 6. `infra/`

```
infra/
├── docker/
│   ├── docker-compose.yml                  # local MongoDB (optional convenience — a native local install works equally well, see DEPLOYMENT.md § 1); no cloud services
│   └── mongo-init/                         # seed data scripts (categories, demo courses, learning paths)
└── ci/                                     # any CI config shared across workflows (composite actions, etc.) — build/lint/test only, no deploy step
```

---

## 7. Naming Conventions

- **Backend packages:** `com.mentora.backend.<module>` — matches [`BACKEND_ARCHITECTURE.md`](./BACKEND_ARCHITECTURE.md)'s module list exactly, so a reviewer can find a feature's code from its product-domain name with no translation step.
- **Shared KMP packages:** `com.mentora.shared.<layer>.<feature>` (e.g. `com.mentora.shared.domain.quiz`, `com.mentora.shared.data.repository.quiz`).
- **Web routes:** kebab-case URL segments matching [`../product/INFORMATION_ARCHITECTURE.md`](../product/INFORMATION_ARCHITECTURE.md)'s already-defined paths exactly (`/app/my-learning`, `/instructor/courses/:id/curriculum`) — this file does not redefine routes, it just confirms the Next.js `app/` directory structure realizes them.
- **Generated files** are marked `// GENERATED — DO NOT EDIT. Source: design-system/design-tokens.json` (or the CSS/Swift equivalent comment) at the top of every output file, and this comment is itself part of the Style Dictionary template — not something an implementer adds by hand and could forget.

## 8. What Is Explicitly Not Created In This Phase

Per this architecture phase's constraints, none of the directories above contain real code yet — this file defines where code *will* go once [`IMPLEMENTATION_ROADMAP.md`](./IMPLEMENTATION_ROADMAP.md) begins, starting at Milestone M0.
