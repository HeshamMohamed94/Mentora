# Mentora — Web Architecture

Framework decision: [ADR-001](./adr/ADR-001-web-frontend-framework.md) (Next.js/React/TypeScript). This file details how the six Web-facing IA surfaces (Public Web, Student Web, Instructor Web, Admin Web, each in English and Arabic) are realized in one Next.js application.

---

## 1. Rendering Strategy Per Surface

| Surface | Rendering | Why |
|---|---|---|
| Public (Landing, Explore, Course Details, Learning Paths, Login, Register) | Server-rendered (SSR) or statically generated (SSG) via React Server Components, revalidated on a schedule/on-demand for catalog changes | Real SEO — crawlable HTML, per-course `<title>`/meta/Open Graph tags, fast first paint for the "evaluator" persona's first click ([`../product/PRODUCT_SPEC.md § 4`](../product/PRODUCT_SPEC.md)) |
| Authenticated Student app (Dashboard, My Learning, Course Player, Quiz, AI Tutor, Certificates, Profile, Settings) | Client-rendered after an authenticated shell loads (React Server Components for the shell/layout, Client Components for interactive state) | No SEO need (behind auth); prioritizes interactivity (video playback, chat, optimistic "Mark Complete") over first-paint SEO cost |
| Instructor Web / Admin Web | Same as authenticated Student app — client-rendered, internal-tool character | Same reasoning; these are Web-only per [`../product/USER_ROLES.md`](../product/USER_ROLES.md) |

All three tiers are ordinary Next.js App Router routes in one project — not three deployments (see [ADR-001](./adr/ADR-001-web-frontend-framework.md)'s consequences).

## 2. Route Groups

Mirrors [`../product/INFORMATION_ARCHITECTURE.md`](../product/INFORMATION_ARCHITECTURE.md) exactly — this file does not redefine navigation structure, only the Next.js mechanism realizing it:

```
app/[locale]/
  (public)/            → Landing, Explore, Course Details, Learning Paths, Login, Register
  (app)/                → Dashboard, My Learning, Course Player, Quiz, AI Tutor, Certificates, Profile, Settings
  (instructor)/          → Instructor Dashboard, Course Editor (Overview/Curriculum), Lesson Editor, Quiz Editor
  (admin)/               → Admin Dashboard, Manage Courses/Users/Instructors/Categories
```

`[locale]` is `en` or `ar` (see [`LOCALIZATION_ARCHITECTURE.md § 2`](./LOCALIZATION_ARCHITECTURE.md)). `middleware.ts` handles: locale detection/redirect on first visit, and an auth gate redirecting an unauthenticated request to `(app)`/`(instructor)`/`(admin)` routes to `/login` (mirroring the enroll-gate/login-gate behavior in [`../product/USER_FLOWS.md`](../product/USER_FLOWS.md)).

## 3. Same-Origin Backend Proxy

Next.js rewrites `/api/*` to the Ktor backend's real address (configured per environment — see [`DEPLOYMENT.md § 4`](./DEPLOYMENT.md)). This is a deliberate architectural choice, not an implementation detail: it makes the backend's auth cookies **first-party** to the browser regardless of the backend's actual address, which is what allows `SameSite=Lax` (rather than the weaker `SameSite=None`) in [ADR-006](./adr/ADR-006-authentication-strategy.md) and keeps CSRF protection simple (§ [`AUTH_SECURITY.md § 10`](./AUTH_SECURITY.md)). **For the current MVP** ([ADR-012](./adr/ADR-012-local-demo-scope.md)), that real address is simply `http://localhost:8080`, read from `.env.local`'s `API_BASE_URL` — the Web app runs locally via `next dev` and requires no cloud hosting/deployment (Vercel or otherwise); see [`DEPLOYMENT.md § 4a`](./DEPLOYMENT.md) for the full local-networking picture across all clients.

## 4. Styling & Theming

Tailwind CSS v4, `@theme` block mapping Tailwind's utility scale onto the generated `styles/tokens.css` custom properties (§ [ADR-011](./adr/ADR-011-design-token-pipeline.md)) — `bg-brand-primary` resolves to `var(--color-brand-primary)`, never a Tailwind-default color. Light/dark theme: `[data-theme="light"]`/`[data-theme="dark"]` on `<html>`, toggled by a small client-side script reading system preference + an explicit user override (persisted per [`../design-system/DESIGN_SYSTEM.md`](../design-system/DESIGN_SYSTEM.md)'s light/dark requirement — Settings' theme toggle, [`../product/SCREEN_INVENTORY.md § 17`](../product/SCREEN_INVENTORY.md)). RTL: `<html dir={locale === 'ar' ? 'rtl' : 'ltr'}>` set in the root layout from the resolved locale; every Tailwind spacing/alignment utility used is the **logical** variant (`ps-*`/`pe-*`/`ms-*`/`me-*`/`text-start`/`text-end`), never `pl-*`/`pr-*`/`text-left` — enforced by an ESLint rule (`eslint-plugin-tailwindcss` custom rule or a simple regex-based lint check) flagging physical-direction utility classes, so [`../design-system/DESIGN_RULES.md` rule 16](../design-system/DESIGN_RULES.md) is caught in CI, not just in code review.

## 5. Data Layer

TanStack Query for all server state: every domain (`courses`, `enrollment`, `progress`, `quiz`, `certificates`, `learningPaths`, `aiTutor`) gets a small `lib/api/<domain>.ts` module of typed query/mutation hooks wrapping the shared fetch client. Optimistic updates are used narrowly and deliberately (e.g. "Mark Complete" flips the UI immediately, reconciled against the server's authoritative response — never assumed correct without reconciliation, per [`ARCHITECTURE.md § 4.1`](./ARCHITECTURE.md)'s server-authoritative rule). React Hook Form + Zod for Login/Register/Course Editor/Quiz Editor forms — client-side validation schemas are a UX convenience, never a substitute for the backend's own validation (§ [`AUTH_SECURITY.md § 8`](./AUTH_SECURITY.md)).

## 6. Course Player (Web-Specific)

Per [`../ux/WEB_UX.md § 3`](../ux/WEB_UX.md): the Sidebar collapses by default on Course Player/Quiz to maximize width for the two-column video+curriculum layout. Implemented as a client-side layout state (not a route change) — collapsing the sidebar is a `Client Component` state toggle, persisted per-session (not per-account) via `sessionStorage`, matching [`../ux/WEB_UX.md § 1`](../ux/WEB_UX.md)'s "remembers that preference for the session" note.

## 7. Accessibility & SEO Mechanics

- Every public route sets locale-appropriate `<title>`/`<meta name="description">`/Open Graph tags server-side (via Next.js `generateMetadata`) — a course's page title and description are real, crawlable, and shareable, in the course's own content or the active UI locale as appropriate.
- `hreflang` alternate links between `/en/...` and `/ar/...` equivalents of every public page, so search engines correctly index both language versions as alternates rather than duplicate content.
- A skip-to-content link at the top of every page using the Navbar/Sidebar layout, per [`../design-system/ACCESSIBILITY.md § 3`](../design-system/ACCESSIBILITY.md).

## 8. Why Not KMP-Shared Web UI (restated briefly)

Full analysis in [ADR-001](./adr/ADR-001-web-frontend-framework.md). In one sentence: Compose Multiplatform for Web cannot currently deliver the SEO/accessibility/ecosystem bar this specific Web surface requires, and the Design System's own Web mapping is already CSS/DOM-native — forcing KMP here would fight both the product requirement and the design system's own architecture.
