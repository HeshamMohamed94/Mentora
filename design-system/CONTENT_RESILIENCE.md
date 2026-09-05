# Mentora Design System — Content Resilience

Every component in [`COMPONENTS.md`](./COMPONENTS.md) is specified for the happy path: a normal-length title, a loaded image, a successful network call. This file specifies what happens when reality doesn't cooperate — long text, missing media, failed requests, empty datasets, expanded translations, and accessibility text scaling — so that behavior is designed once, here, instead of improvised per screen.

**Governing principle:** a component degrades by *reflowing or truncating predictably*, never by breaking layout, silently losing information, or showing a raw technical error to the user.

---

## 1. Text Wrapping & Truncation — Component Reference

| Component / field | Behavior | Lines | Reasoning |
|---|---|---|---|
| CourseCard / CourseProgressCard / LearningPathCard / CertificateCard **title** | Clamp + ellipsis | 2 | Title is the primary scan target in a grid; 2 lines balances legibility against grid rhythm. Full title always available via the card's accessible name/tooltip (see § 5). |
| CourseCard **instructor name** | Clamp + ellipsis | 1 | Secondary metadata; grid rhythm depends on this row staying single-line. |
| InstructorCard **name** | Clamp + ellipsis | 1 | Same reasoning; full name in the card's `heading` accessible role/aria-label. |
| InstructorCard **title/expertise** | Clamp + ellipsis | 1 | |
| StatCard **label** | Clamp + ellipsis | 1 | |
| StatCard **value** | **Never truncates.** If the formatted value doesn't fit at `typography.heading.h2`, drop one type-scale step (to `heading.h3`) before ever clipping a number — a truncated statistic is misinformation, not a display compromise. | n/a | |
| Badge / CategoryChip label | Clamp + ellipsis | 1, no wrap | Chips/badges are short tags by design; if a label needs to wrap it's a content bug upstream (chip copy should be authored short), not a layout problem to solve here. |
| PrimaryButton / SecondaryButton / TonalButton / TextButton label | **Wraps to 2 lines before clipping.** Button height grows to fit; never ellipsis a call-to-action. | up to 2 | A clipped CTA can silently change or hide the action's meaning; a taller button doesn't. |
| MobileBottomNavigation item label | Clamp + ellipsis at 1 line; if the label still doesn't fit at `caption` size in the user's active font-scale, drop the label and show icon-only for that item (icon retains its full accessible name for screen readers even when the visible label is hidden) | 1 (or 0 visible) | Only 5 items, fixed-width — no room to wrap. Icon-only is the documented fallback, not a broken layout. |
| Sidebar / Navbar item label | Clamp + ellipsis | 1 | Sidebar is wide enough this is rare; guards against extreme translation expansion. |
| Tabs label | Clamp + ellipsis | 1 | Tab strip is horizontally scrollable if items overflow (see § 6), so individual labels stay single-line rather than wrapping and breaking the tab row's height. |
| AppDialog **title** | Wraps | up to 2 | Dialog titles are short by convention but never silently clipped — a clipped dialog title could hide what the user is confirming. |
| AppDialog **body** | Wraps freely | unlimited (dialog scrolls if needed) | |
| TextField/PasswordField/SearchField **label** | Wraps (rare — labels are short) | up to 2 | Never truncated: a clipped form label can hide required-field context. |
| TextField **placeholder** | Clamp + ellipsis | 1 | Supplementary hint text, safe to truncate. |
| TextField **helper/error text** | Wraps freely, field height grows | unlimited | Error messages must always be fully readable — never truncated. |
| AITutorBubble | Wraps freely, bubble grows | unlimited | Chat content; truncating an AI or user message is a data-loss bug, not a design choice. |
| Snackbar message | Clamp + ellipsis at 2 lines on narrow viewports if the message plus action can't both fit; prefer shortening the source copy over relying on this | 1–2 | Snackbars are transient and width-constrained by design. |
| EmptyState / ErrorState / SuccessState title & description *(v1.2)* | Wraps freely | title ≤ 1–2, description ≤ 2–3 (soft guidance, not a hard clamp) | Same pattern for all three — `SuccessState` is structurally a sibling of `EmptyState`/`ErrorState` (`COMPONENTS.md § State Patterns`). |
| VideoPlayer current lesson title *(v1.2)* | Clamp + ellipsis | 1 | Secondary metadata inside the control bar; same reasoning as CourseCard's instructor-name row. |
| Checkout course line-item title *(v1.2)* | Clamp + ellipsis | 2 | Same rule as CourseCard's title — Checkout reuses CourseCard's content pattern. |
| FileUpload filename *(v1.2)* | **Middle-truncated** (`name…ext`), 1 line | 1 | Deliberate exception to end-ellipsis: keeps the extension visible so two similarly-named files stay distinguishable. Full filename always available via accessible name/tooltip (§ 7). |
| DataTable cell text (titles, names, emails) *(v1.2)* | Clamp + ellipsis | 1 | Full value always reachable via the row's detail view or an accessible title/tooltip — never silently lost. |
| Select/Dropdown selected value / placeholder *(v1.3)* | Clamp + ellipsis | 1 | Same as `TextField`'s value text — long translated option names (e.g. Arabic category names) must not push the field's fixed height. |
| Select/Dropdown option row text *(v1.3)* | Clamp + ellipsis | 1 | The option **list** scrolls when there are many options; individual rows never wrap to accommodate a long label. |

**Implementation rule:** "clamp + ellipsis" always means a real multi-line clamp (CSS `-webkit-line-clamp`/equivalent, Compose `Text(maxLines=, overflow=TextOverflow.Ellipsis)`, SwiftUI `.lineLimit(n).truncationMode(.tail)`) — never a fixed-height container with `overflow: hidden` and no ellipsis indicator, which hides the fact that content was cut.

---

## 2. Missing / Failed Images

Applies to CourseCard thumbnails, InstructorCard/Avatar photos, CertificateCard previews, and any other user- or content-sourced image.

| Case | Treatment |
|---|---|
| Course/certificate thumbnail fails to load or is absent | Render a placeholder filling the same 16:9 area: `color.surface.variant` background, centered icon (`icon.large`, "school"/"image" Material Symbol as appropriate, `color.text.secondary`) at the card's own radius. Never a broken-image glyph, never a layout collapse to zero height. |
| Avatar image fails to load or is absent | Fall back to initials-on-`brand.primaryContainer` per [`COMPONENTS.md § Avatar`](./COMPONENTS.md) — this is already the documented default state, not a new one; it applies equally to "no photo provided" and "photo failed to load." |
| Instructor/certificate preview partially loads then errors | Same as "fails to load" — no intermediate broken/half-rendered state is shown; the placeholder replaces the attempt entirely. |

Images never reserve zero space while loading or failing — the placeholder/skeleton always occupies the image's final aspect ratio so surrounding layout never reflows once the real image (or its absence) resolves.

---

## 3. Loading States

Governed in full by [`COMPONENTS.md § LoadingState`](./COMPONENTS.md) (skeletons for content areas, spinners for inline/button actions). The content-resilience-specific rule: **a loading state occupies the same footprint its resolved content will** — a CourseCard skeleton is exactly CourseCard-shaped, a StatCard skeleton is exactly StatCard-shaped — so nothing reflows when real content (or an empty/error state) replaces it.

---

## 4. Network Failures

- A failed request that blocks an entire view renders [`COMPONENTS.md § ErrorState`](./COMPONENTS.md) in place of the content area — friendly copy, retry action, never a raw error code or stack trace (already a hard rule in `COMPONENTS.md` and [`ACCESSIBILITY.md`](./ACCESSIBILITY.md)).
- A failed request that affects one item inside a list/grid (e.g. one course card's enrollment action fails) shows the failure at the **component** level — an inline error affordance (e.g. the action button reverts with a brief `Snackbar`: "Couldn't enroll — try again") rather than replacing the whole grid with a full `ErrorState`.
- A failed background sync (e.g. progress not saved) surfaces via `Snackbar`, not a blocking dialog — it shouldn't interrupt what the user is doing to report a recoverable, retryable problem.
- Retry is always a real, re-triggerable action (same token/component as the original request), never a "refresh the page" instruction as the only recourse.

---

## 5. Empty Data

Governed by [`COMPONENTS.md § EmptyState`](./COMPONENTS.md). Content-resilience addition: empty state copy is written per-context (an empty "My Learning" says something different from an empty search-results grid) but every instance still follows the fixed structure (icon → title → description → primary action) — never a bare "No results" with nothing else, and never an empty grid silently rendering nothing with no explanation.

---

## 6. Localization Expansion

Cross-referenced in full from [`LOCALIZATION.md § 6`](./LOCALIZATION.md) — that file owns the *why* (which languages expand, by how much) and the RTL-specific alignment/ellipsis-direction details; this file's § 1 table is the canonical *what happens* per component regardless of which language caused the overflow. One addition specific to resilience rather than direction:

- **Tabs and horizontally-arranged item groups** (Tabs, AITutorQuickAction row) become horizontally scrollable rather than wrapping or compressing individual items below their minimum readable width, whenever the full set doesn't fit — this applies equally to "too many items" and "items whose translated labels got longer."

---

## 7. Accessibility Text Scaling

Cross-referenced in full from [`ACCESSIBILITY.md § High Text Scaling`](./ACCESSIBILITY.md). The resilience-specific consequence: every rule in § 1 of this file is written to also be the correct behavior at 200% browser zoom / largest Android font scale / largest iOS Dynamic Type size — there is no separate "large text mode" layout. A component that clamps to 2 lines at default text size still clamps to 2 lines (of larger text, so more character-width is lost) at maximum scale; a button that wraps rather than clips at default size still wraps rather than clips at maximum scale. If a component would need genuinely different structure at large scale (e.g. `MobileBottomNavigation`'s icon-only fallback, § 1), that fallback is defined here and in `ACCESSIBILITY.md`, not invented ad hoc per screen.

---

## 8. Locked Rule

No component in [`COMPONENTS.md`](./COMPONENTS.md) may use a fixed-height text container without an explicit entry in the § 1 table above (or without inheriting one from a parent component it extends, e.g. `PasswordField`/`SearchField` from `TextField`). Adding a new component requires adding its wrap/truncate behavior to § 1 in the same change — see [`DESIGN_RULES.md § Proposing a New Token or Component`](./DESIGN_RULES.md).
