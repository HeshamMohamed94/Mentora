# ADR-001: Web Frontend Framework

**Status:** Locked — **Approved** (explicit product-owner sign-off recorded 2026-09-04)
**Date:** 2026-09-04

## Decision

The Mentora website is built with **Next.js 14+ (App Router), React, and TypeScript** — one Next.js application serving both the public/SEO surface and the authenticated Student/Instructor/Admin app, not two separate frontends.

## Context

The Web surface must simultaneously be:
- A **public, SEO-indexable marketing and course-discovery site** (Landing, Explore, Course Details, Learning Paths — all reachable and crawlable by search engines and shareable with real link previews).
- A **rich, stateful authenticated application** (Dashboard, Course Player, Quiz, AI Tutor, Instructor course authoring, Admin management tables).
- Bilingual (English/Arabic), RTL-capable, light/dark themed, and rendered from the same Mentora Design System tokens Android and iOS use.

[`../product/USER_ROLES.md`](../../product/USER_ROLES.md) and [`../ux/WEB_UX.md`](../../ux/WEB_UX.md) confirm Guest browsing must work without an account and without a client-side-only rendering penalty — a crawler or a `curl` request to `/courses/some-course` must receive real content, not an empty `<div id="root">` shell.

## Options Considered

### Option A — Next.js (React/TypeScript), chosen

A single framework that renders public/SEO pages via Server-Side Rendering or Static Generation, and the authenticated app via the same routing/data-fetching model with client-side interactivity where needed (Course Player, AI Tutor chat, Instructor's drag-to-reorder curriculum editor). One codebase, one deploy target, one component library, one design-token consumption path.

### Option B — Kotlin Multiplatform / Compose Multiplatform for Web (Wasm)

Sharing UI code with Android via Compose Multiplatform's Web target was explicitly named in the brief as something to evaluate rather than assume away.

**Why not:** Compose for Web (Kotlin/Wasm) renders to `<canvas>`-backed or DOM-backed components with accessibility and SEO tooling that is meaningfully behind React's in 2026 — screen-reader support, crawlability, and browser devtools integration are all secondary concerns for a toolkit whose primary target is Android. Mentora's own [`../design-system/platform-mapping.md`](../../design-system/platform-mapping.md) already defines Web's token contract in CSS-custom-property terms (`--color-brand-primary`, `[data-theme]`) — a CSS/DOM-first design, not a Compose-first one. Public marketing/course pages need real SSR-driven SEO (title/meta tags per course, Open Graph previews, crawlable HTML) — Compose for Web has no server-rendering story comparable to Next.js's. Forcing this would also mean the Web team inherits Kotlin/Compose idioms for a surface (marketing pages, forms, tables) where React's ecosystem (form libraries, table components, i18n libraries, accessibility-audited component patterns) is simply deeper. **This is exactly the case the brief asked to flag: sharing Web UI through KMP is a poor tradeoff here, and this ADR says so explicitly rather than defaulting to maximum code sharing.**

### Option C — Plain React SPA (Vite), no framework-level SSR

Simpler build, faster local dev loop, no App Router learning curve.

**Why not:** fails the SEO requirement outright for Landing/Explore/Course Details — a pure client-rendered SPA either needs a bolted-on prerendering solution (react-snap, a headless-Chrome prerender service) or accepts poor SEO, both worse than a framework that solves this natively. Would also mean building two frontends in practice (a static marketing site + a separate app shell) to work around the gap, which is exactly the two-stack outcome Option A avoids.

### Option D — SvelteKit

Comparable SSR/SSG capability to Next.js, smaller runtime, arguably better raw performance.

**Why not chosen:** smaller component/library ecosystem for the specific things Mentora's Web surface needs at scale (i18n libraries with strong RTL support, table/data-grid components for Admin, form libraries) — not a rejection of Svelte's quality, but Next.js's larger 2026 ecosystem and hiring/portfolio-recognition value tip the balance for a project whose explicit goals include reading as a credible, reviewable SaaS codebase to a technical reviewer.

## Consequences

- One Next.js app serves six IA surfaces (Public Web, Student Web, Instructor Web, Admin Web, plus EN/AR variants of each) — route groups separate `(public)`, `(app)`, `(instructor)`, `(admin)` concerns within one codebase (see [`WEB_ARCHITECTURE.md`](../WEB_ARCHITECTURE.md)).
- Every Web-consumed token must exist as a CSS custom property — the token pipeline's Web target is CSS, not a JS object (see [ADR-011](./ADR-011-design-token-pipeline.md)).
- SEO-critical routes (Landing, Explore, Course Details, Learning Paths) use React Server Components / SSR; authenticated, highly-interactive routes (Course Player, Quiz, AI Tutor) lean on client components — both are first-class in the same App Router project, so this isn't a compromise.

## Migration Path

If the Web app's interactive-app half ever outgrows Next.js's App Router model (unlikely at MVP scale), the public/SEO half and the authenticated-app half could be split into two deployments later without changing the underlying React component/design-token layer — this is a low-regret decision at MVP scale.
