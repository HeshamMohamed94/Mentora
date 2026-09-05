# Mentora UX — Website Experience

The Web experience across Guest and Student surfaces. Full per-screen field-by-field specs (content order, states, accessibility, wireframes) live in [`SCREEN_UX_SPECS.md`](./SCREEN_UX_SPECS.md) — this file defines the **layout system** every Web screen shares and how each listed screen sits within it. Grid/spacing/breakpoint values are owned by [`RESPONSIVE_BEHAVIOR.md`](./RESPONSIVE_BEHAVIOR.md) and the design system; nothing here introduces a new value.

---

## 1. Layout System

### Top Navbar (Public / pre-content-area on Student pages too)

- **Height:** 64 (`COMPONENTS.md § Navigate`).
- **Sticky behavior:** the public `Navbar` is **sticky on scroll** (`position: sticky; top: 0`) on Landing, Explore, Course Details, and Learning Paths — these are browse-and-compare pages where quick access back to Explore/Search/Login is genuinely useful while scrolling a long page. It is **not** sticky on Login/Register (short, single-purpose forms with nothing to scroll back up for).
- **Authenticated pages do not show the public Navbar** — the `Sidebar` (below) replaces it as the primary chrome once a Student is logged in. A slim top bar may still exist above the content area for page title/breadcrumb + account menu, but it is not the marketing `Navbar` component.

### Authenticated Sidebar

- **Width:** 264 expanded / 72 collapsed (`COMPONENTS.md § Sidebar`).
- **Sticky behavior:** always fixed/sticky for the full viewport height — it never scrolls with page content, consistent with it being the primary navigation anchor for the entire authenticated session.
- **Collapse trigger:** user-toggleable (a collapse control at the bottom of the Sidebar) in addition to the automatic Tablet-width icon-only collapse defined in `RESPONSIVE_BEHAVIOR.md § 1` — a Desktop user can manually collapse it for more content width (e.g. while in Course Player), and it remembers that preference for the session.
- **Active state:** the current section's Sidebar item shows the `active` state (`color.brand.primaryContainer` background) per `COMPONENTS.md` — always exactly one item active, never zero (Dashboard is the default/fallback active state if a page doesn't map cleanly to one item, e.g. Settings nested under Profile still shows Profile as active).
- **Language selector *(v1.3, locked MVP):*** neither the public `Navbar` nor the authenticated `Sidebar` carries a language control directly — it lives inside Settings (`SCREEN_UX_SPECS.md § 17`), one level in from Profile, consistent with keeping both nav components at their existing locked item sets (`../product/PRODUCT_SPEC.md § 16`, `../product/INFORMATION_ARCHITECTURE.md`).

### Content Max Widths & Grid

Owned entirely by `RESPONSIVE_BEHAVIOR.md §§ 2–4`. Applied consistently: marketing pages cap at 1200–1280px, the app shell caps at 1440px, forms cap narrower still and center.

### Course Card Layout

`CourseCard` at 280–340px fluid width within its grid column (`RESPONSIVE_BEHAVIOR.md § 3`), 16:9 thumbnail, content hierarchy per `COMPONENTS.md § Cards` — unchanged from the design system, referenced here only to confirm Web uses the standard card, not a Web-specific variant.

### Page Hierarchy (header structure, by page type)

| Page type | Header structure |
|---|---|
| Marketing (Landing) | Navbar → Hero → content sections (no secondary page title — the hero *is* the header) |
| Browse/listing (Explore, Learning Paths, My Learning, Certificates, Admin lists) | Navbar/Sidebar → page title (`heading.h1`) + primary action (if any, e.g. nothing on Explore, "Create Course" on Instructor Dashboard) → filter/search row (if applicable) → content grid/list |
| Detail (Course Details, Learning Path Details, Certificate Detail) | Navbar/Sidebar → hero/summary block (image + title + key facts) → body content sections in defined order (see `SCREEN_UX_SPECS.md`) |
| Form (Login, Register, Checkout, Lesson/Quiz Editor) | Minimal chrome → centered form card, `heading.h3` form title, no competing page-level heading |
| Immersive (Course Player, Quiz, AI Tutor) | Minimal/no Sidebar competing for space (Sidebar collapses or the surface takes over — see `SCREEN_UX_SPECS.md` per screen) — these pages prioritize the task over navigation chrome |

---

## 2. Public Website Walkthrough

| Screen | Web-specific notes |
|---|---|
| **Landing** | Sticky Navbar; hero at marketing max-width (1200–1280); featured `CourseCard`/`LearningPathCard` rows use the standard course-grid columns for the current breakpoint. |
| **Course discovery (Explore)** | Sticky Navbar; `SearchField` + filter chips row sits directly under the page title, above the grid; grid reflows per `RESPONSIVE_BEHAVIOR.md § 3`. |
| **Search/filter** | Not a separate screen — a state of Explore (`../product/SCREEN_INVENTORY.md`); the same layout, with the filter row showing active-selected `CategoryChip`s and a result count. |
| **Course details** | Sticky Navbar; hero block (thumbnail/title/instructor/price/CTA) at the top, curriculum outline and instructor detail below — full content order in `SCREEN_UX_SPECS.md`. |
| **Registration / Login** | Navbar present but not sticky (nothing to scroll); centered form at form-width cap; no Sidebar (pre-auth). |
| **Demo Checkout** | Authenticated-only, so Sidebar context applies once past the auth gate — centered `Checkout` card at form-width cap, per `RESPONSIVE_BEHAVIOR.md § 10`. |

## 3. Student Website Walkthrough

| Screen | Web-specific notes |
|---|---|
| **Dashboard** | Sidebar + page content — "Continue learning" module first (highest-priority content, above the fold), then recommendations/stats. |
| **My Learning** | Sidebar + filterable grid of `CourseProgressCard`s, same grid rules as Explore. |
| **Course Player** | Sidebar **collapses by default** (or is manually collapsible, § 1) to maximize width for the two-column video+curriculum layout (`RESPONSIVE_BEHAVIOR.md § 7`) — this is the one page where minimizing navigation chrome is deliberately prioritized. |
| **Quiz** | Same reduced-chrome treatment as Course Player — a focused, single-column question flow, Sidebar collapsed. |
| **AI Tutor** | Sidebar stays visible (it's a navigation-reachable destination, not an immersive task) at Tablet+/Desktop widths; full-height chat panel to the right of/within the content area per `RESPONSIVE_BEHAVIOR.md § 9`. |
| **Learning Paths** | Sidebar + grid of `LearningPathCard`s, same grid rules. |
| **Certificates** | Sidebar + grid of `CertificateCard`s. |
| **Profile** | Sidebar + a single centered content column (not a full-width grid — profile info doesn't need grid density). |

---

## 4. Desktop/Tablet Responsive Summary

Fully owned by `RESPONSIVE_BEHAVIOR.md` — this section exists only to confirm Web-specific behavior doesn't diverge from it:

- Tablet: Sidebar → icon-only; Navbar links may condense; grids drop to 2 columns (course grid) / 8 columns (layout grid).
- Desktop/Large Desktop: full Sidebar, full Navbar, grids at their maximum column counts, content capped at 1440.
- No Web screen requires a Mobile-specific layout fork — Web's own Mobile-width behavior (§ `RESPONSIVE_BEHAVIOR.md § 1`) is a graceful-degradation state (off-canvas Sidebar, hamburger Navbar), not the primary target; the native app is the primary Mobile experience (`MOBILE_UX.md`).

---

## 5. Avoiding Dashboard Over-Complexity

Per the brief's explicit instruction, the Student Dashboard stays to exactly three modules, in this order, and does not grow beyond them without a documented reason: (1) Continue Learning, (2) Recommended/Next Courses, (3) Learning Paths in progress + quick stats. No widget grid, no configurable layout, no more than one `StatCard` row — see `SCREEN_UX_SPECS.md § Dashboard` for the exact content order.
