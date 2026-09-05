# Mentora UX — Responsive Behavior

Exact adaptation rules per breakpoint, built strictly on [Mentora Design System v1.3.1](../design-system/DESIGN_SYSTEM.md)'s locked breakpoints, grid, spacing, and course-grid tokens. **No value here is invented — every number is a direct reference to `design-tokens.json`.** This file governs *behavior* (what changes shape, what disappears, what reflows); the *values* it uses are 100% owned by the design system.

Breakpoints (`design-tokens.json → breakpoints`): **Mobile** 0–599 · **Tablet** 600–1023 · **Desktop** 1024–1439 · **Large Desktop** 1440+.

---

## 1. Navigation

| Breakpoint | Public Web | Student Web | Instructor/Admin Web | Student Mobile (Android/iOS) |
|---|---|---|---|---|
| Mobile (0–599) | `Navbar` collapses to logo + hamburger menu (links/search/auth move into a slide-out or full-screen menu) | `Sidebar` collapses entirely — mobile Student experience uses the native app's `MobileBottomNavigation` instead (Web at mobile width is treated as "use the app" territory, but if accessed, `Sidebar` becomes an off-canvas drawer triggered by a menu icon) | Same off-canvas `Sidebar` pattern as Student Web (Instructor/Admin at mobile width is a rare/unsupported-by-design case — see § 8) | N/A — `MobileBottomNavigation`, 5 fixed items, always visible |
| Tablet (600–1023) | Full `Navbar` links visible if they fit; search may collapse to an icon that expands on tap | `Sidebar` collapses to icon-only (72px, per `COMPONENTS.md`'s collapsed width) — labels appear on hover/tap | Same icon-only `Sidebar` | N/A (tablet is out of scope for the native app's own breakpoint system — see § 8) |
| Desktop (1024–1439) | Full `Navbar`, all links + search + auth actions visible | `Sidebar` expanded (264px), full labels | Same expanded `Sidebar` | N/A |
| Large Desktop (1440+) | Same as Desktop, content capped at `maxContentWidth` (1440) | Same as Desktop | Same as Desktop | N/A |

## 2. Grid & Page Padding

Direct application of `design-tokens.json → grid` and `spacing.pagePadding`:

| Breakpoint | Grid columns | Gutter | Page padding |
|---|---|---|---|
| Mobile | 4 | 16 | 16 (`space.4`) |
| Tablet | 8 | 24 | 24 (`space.6`) |
| Desktop | 12 | 24 | 32 (`space.8`) |
| Large Desktop | 12 | 24 | 48 (`space.12`) |

Applies to every grid-based layout: Explore's course grid, My Learning, Certificates, Admin lists (in table form), Learning Paths.

## 3. Course Grid (CourseCard columns)

Direct application of `design-tokens.json → courseGrid` — used on Explore, My Learning, Home/Dashboard's recommendation modules, and Learning Path Details' member-course list:

| Mobile | Tablet | Desktop | Large Desktop |
|---|---|---|---|
| 1 column, full-width card | 2 columns | 3 columns | 4 columns |

`CourseCard` itself does not change design between breakpoints (`../design-system/DESIGN_SYSTEM.md § 9–19`) — only column count and card width shift (280–340px fluid on Web; full-width on Mobile).

## 4. Content Max Width

| Context | Max width |
|---|---|
| Overall page/app shell | 1440px (`grid.maxContentWidth`) — content centers with equal margins beyond this on ultra-wide displays |
| Marketing content (Landing hero, feature sections) | 1200–1280px (`grid.preferredMarketingWidth`) — narrower than the full 1440 for readability of long-form marketing copy |
| Course Player video + curriculum (Desktop/Large Desktop) | Full 1440 max, split per § 9 |
| Forms (Login, Register, Checkout, Lesson/Quiz Editor fields) | Capped narrower still (~480–560px) regardless of breakpoint, centered — forms don't benefit from stretching to full grid width; this is a layout decision, not a token, so the exact value is an implementation detail within the "narrower than content max width" rule |

## 5. Dialogs & Sheets

| Breakpoint | AppDialog | BottomSheet |
|---|---|---|
| Mobile | `AppDialog` becomes full-screen or near-full-screen (standard mobile dialog convention) rather than a small centered card — action buttons stack full-width per the design system's existing mobile button-stacking rule (`COMPONENTS.md § AppDialog`) | Native pattern — slides up from the bottom, primary use of dialogs on mobile for anything more than a simple confirm |
| Tablet/Desktop/Large Desktop | Centered card, `radius.xlarge`, action buttons right-aligned in a row (`COMPONENTS.md`) | Used sparingly on Web (e.g. a filter panel) — same visual spec, anchored to the bottom of the viewport or a relevant panel |

## 6. Tables → Collapsed Cards (DataTable)

Per `../design-system/COMPONENTS.md § DataTable` (v1.2), applied concretely:

| Breakpoint | Rendering |
|---|---|
| Mobile / Tablet (< 1024, i.e. below `desktop`) | Collapses to stacked cards — one card per row, each column rendered as a `label: value` pair (`component.dataTable.collapsedCard`). Row actions (`more_vert`) remain available per card. |
| Desktop / Large Desktop (≥ 1024) | Renders as a true table — header row, sortable columns, row hover, row actions. |

This applies to every Admin list (Manage Courses/Users/Instructors/Categories) and Instructor Dashboard's course list if rendered in table form. **Never** a horizontally-scrolling cramped table below `desktop` — the collapse is a hard rule, not a fallback of last resort.

## 7. Course Player

| Breakpoint | Layout |
|---|---|
| Mobile | Video (`VideoPlayer`, 16:9, full width) at the top; lesson title/description/tabs below; curriculum lives in a `BottomSheet` (triggered by an affordance, e.g. a "Curriculum" button), never permanently consuming horizontal width. See `MOBILE_UX.md § Course Player`. |
| Tablet | Same vertical stack as Mobile (video top, content below, curriculum in a `BottomSheet`) — tablet width isn't reliably wide enough for a persistent side-by-side layout without cramping the video. |
| Desktop | Two-column layout: primary video + lesson content area (≈70% width) + persistent curriculum sidebar (≈30% width, scrollable independently). See `WEB_UX.md § Course Player`. |
| Large Desktop | Same two-column layout, both columns get more breathing room within the 1440 max width; video area does not scale indefinitely past a sensible max (avoids an oversized, low-density video at ultra-wide sizes) — the curriculum sidebar absorbs the extra space instead. |

## 8. Instructor/Admin Data Views

Instructor and Admin are Web-only by product decision (`../product/USER_ROLES.md`) — they are not shipped as native mobile apps, but the **Web** surface is still responsive within Web's own breakpoints (someone resizing a browser window, or using a smaller laptop):

| Breakpoint | Treatment |
|---|---|
| Desktop / Large Desktop | Full `Sidebar` + `DataTable`/table views as designed — the primary, expected usage context for these roles. |
| Tablet | `Sidebar` collapses to icon-only (§ 1); `DataTable` collapses to cards (§ 6); Course Editor's Overview/Curriculum tabs remain side-by-side-capable content but stack more tightly. |
| Mobile | Full off-canvas `Sidebar` + collapsed-card data views — supported for completeness (a browser window can always be this narrow) but not a design priority; no Instructor/Admin-specific mobile optimization pass beyond "doesn't break." |

## 9. AI Tutor

| Breakpoint | Layout |
|---|---|
| Mobile | Full-screen chat surface (its own bottom-nav tab) — message input pinned above the keyboard, quick-action row scrolls horizontally above the input. |
| Tablet/Desktop | Full-height panel within the app shell (Sidebar remains visible) — same internal layout (chat thread + quick actions + input), just more horizontal breathing room for message bubbles (max-width capped per § 4's form-width logic applied to bubble width, so lines don't stretch unreadably wide). |
| Contextual (from Course Player) | Web: opens as a docked panel alongside the player (replacing or overlaying the curriculum sidebar area) rather than navigating away. Mobile: opens as its own pushed screen (per `NAVIGATION_SPEC.md § 3`), since there's no room for a docked panel at mobile width. |

## 10. Checkout

| Breakpoint | Layout |
|---|---|
| Mobile | Single column, full-width `Checkout`/`OrderSummary` card, confirm action full-width and reachable without excessive scrolling (order summary is short by design — course, price, notice, button). |
| Tablet/Desktop/Large Desktop | Centered card at the form-width cap (§ 4), not stretched to the full grid — a checkout summary reads better narrow than full-width regardless of screen size. |

## 11. Typography

Per `../design-system/DESIGN_SYSTEM.md § 2.2` (unchanged, referenced not restated): only `display.*` and `heading.h1`/`h2` scale down below Desktop; everything else (`heading.h3`/`h4`, `body.*`, `label.*`, `caption`) is constant across all four breakpoints.

## 12. What Does *Not* Change Across Breakpoints

Explicit, since the brief warns against "just shrinking Desktop into Mobile":

- Component **identity** never changes — a `CourseCard` is the same component at every breakpoint (§ 3); a `Toggle` doesn't become a different control on mobile.
- **Color, radius, elevation, motion tokens** are breakpoint-independent by design (they're theme-dependent, not viewport-dependent).
- **Instructor/Admin role boundaries** don't shift with viewport — resizing a browser window never grants Admin capabilities on a Student session or vice versa.
- **Navigation destinations** are the same set of screens across breakpoints — what changes is how they're *reached* (Sidebar vs. off-canvas drawer vs. bottom nav), never which screens exist.

## 13. Select / Dropdown *(v1.3, new)*

Per `../design-system/COMPONENTS.md § Select / Dropdown` and `../design-system/platform-mapping.md`, applied concretely:

| Breakpoint / platform | Rendering |
|---|---|
| Mobile (Android/iOS) | Native picker pattern (`ExposedDropdownMenuBox` / `Menu`-`Picker`) — full-width field, options presented via the platform's own picker surface, still resolving to Mentora's field/menu tokens for color/typography where the native chrome allows. |
| Mobile Web / Tablet / Desktop | The field itself is always full-width of its form column at every breakpoint (same as `TextField`) — only the **menu** changes: at Mobile Web width it behaves like the field's own dropdown anchored below it (no room for a floating menu offset); at Tablet/Desktop it's a floating popover anchored to the field per `COMPONENTS.md`'s existing popover convention (shared with `videoPlayer.speedControl`, `dataTable.rowActions`). |
| Every breakpoint | The field's identity (label, placeholder, height, border, radius, typography) never changes — consistent with § 12's "component identity never changes" rule. Long option labels and long selected values follow the same wrap/truncate rules as everywhere else (`../design-system/CONTENT_RESILIENCE.md § 1`), never causing horizontal scroll or breaking the field's fixed height. |
