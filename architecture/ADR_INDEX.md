# Mentora — Architecture Decision Record Index

Every ADR follows the same shape: Decision → Context → Options Considered (with the strongest alternative and why it wasn't chosen) → Consequences → Migration Path. Full text in [`adr/`](./adr/).

| ADR | Decision | Status |
|---|---|---|
| [ADR-001](./adr/ADR-001-web-frontend-framework.md) | Web frontend: **Next.js (React/TypeScript)**, not Kotlin Multiplatform / Compose for Web | Locked — Approved |
| [ADR-002](./adr/ADR-002-kmp-sharing-boundary.md) | KMP shares domain/data/network only; UI is always native (Compose / SwiftUI) | Locked |
| [ADR-003](./adr/ADR-003-backend-architecture-style.md) | Backend is a **modular monolith** (Ktor), not microservices | Locked |
| [ADR-004](./adr/ADR-004-database-engine.md) | **MongoDB**, validated against PostgreSQL — **local Community Edition for the MVP**, Atlas is optional future evolution | Locked (amended, [ADR-012](./adr/ADR-012-local-demo-scope.md)) |
| [ADR-005](./adr/ADR-005-mongodb-modeling-strategy.md) | Bounded data embeds; unboundedly-growing data is always its own referenced collection | Locked |
| [ADR-006](./adr/ADR-006-authentication-strategy.md) | JWT access + rotating opaque refresh; httpOnly cookies (Web) / secure storage (mobile); BCrypt | Locked |
| [ADR-007](./adr/ADR-007-api-style.md) | **REST**, not GraphQL | Locked |
| [ADR-008](./adr/ADR-008-media-storage.md) | **Local filesystem storage** behind a `MediaStorage` abstraction, served via controlled Ktor endpoints; never binary-in-MongoDB. S3-compatible object storage (R2/S3) is optional future evolution | Locked (amended, [ADR-012](./adr/ADR-012-local-demo-scope.md)) |
| [ADR-009](./adr/ADR-009-ai-provider-abstraction.md) | Backend-mediated `AiProvider` interface; **initial provider is the Anthropic Claude API** | Locked — Approved |
| [ADR-010](./adr/ADR-010-repository-strategy.md) | **Monorepo** | Locked |
| [ADR-011](./adr/ADR-011-design-token-pipeline.md) | **Style Dictionary** generation; tokens live outside the KMP shared module | Locked |
| [ADR-012](./adr/ADR-012-local-demo-scope.md) | **MVP is a local portfolio/demo system** — no cloud hosting, no managed database/object storage, no app-store distribution required | Locked |

## Decisions Requiring Your Explicit Approval

**None remain open.** Both previously-flagged items are now resolved by explicit product-owner approval (2026-09-04):

1. **AI provider selection** (ADR-009) — **approved: the Anthropic Claude API**, as the initial `AiProvider` implementation. The `AiProvider` abstraction is retained so the concrete provider/model can change later without redesigning clients or domain architecture; the API key remains backend-only (never exposed to Web, Android, or iOS); no permanent Claude model identifier is hardcoded into the architecture.
2. **Web framework choice itself** (ADR-001) — **approved: Next.js (React/TypeScript)**, as analyzed.

With the MVP locked as local-only ([ADR-012](./adr/ADR-012-local-demo-scope.md)), the former hosting-account and Apple Developer Program approval items remain **not MVP dependencies** — they're listed under "Optional Future Production Evolution" in [`DEPLOYMENT.md`](./DEPLOYMENT.md) and only become relevant if a future decision is made to move beyond local-demo scope.

**Deferred, not required for MVP sign-off** (only relevant if production evolution is pursued later — see [`DEPLOYMENT.md § 6`](./DEPLOYMENT.md)): staging/production hosting accounts (Railway/Fly.io, MongoDB Atlas, Vercel, Cloudflare R2) and Apple Developer Program enrollment for TestFlight/App Store distribution.

**Architecture status: all decisions requiring explicit approval are resolved. Mentora Technical Architecture is APPROVED and LOCKED.**
