# Mentora UX — Shared States

One state design language, reused everywhere. Every state below maps to an existing [Mentora Design System v1.3.1](../design-system/COMPONENTS.md) component/token set — **this file defines behavior and copy patterns, not new visuals.** [`SCREEN_UX_SPECS.md`](./SCREEN_UX_SPECS.md) references these by name (e.g. "Loading: see `UX_STATES.md § 1`") instead of re-describing them per screen.

---

## 1. Loading / Skeleton

**Component:** `LoadingState` (`../design-system/COMPONENTS.md § State Patterns`).

| Context | Treatment |
|---|---|
| Full content area first load (Explore grid, My Learning, Admin lists) | Skeleton matching the final layout's shape (skeleton `CourseCard`s in a grid, skeleton table rows) — never a centered spinner replacing an entire content area. |
| Inline/button action (Login submit, Complete Demo Purchase, Save in Course Editor) | The triggering `PrimaryButton`'s own Loading state (inline spinner, label hidden, width preserved) — never a full-screen overlay for a sub-second action. |
| Full-screen initial app load (cold start only) | Centered spinner, brand `color.brand.primary` — the only place a bare spinner is acceptable, and only pre-first-paint. |
| Video (Course Player) | Skeleton filling the 16:9 frame at the same aspect ratio, per `COMPONENTS.md § Media & Playback`. |
| AI Tutor response | "Thinking" indicator — see § 10. |
| Select / Dropdown with a fetched option list *(v1.3, e.g. Course Editor's Category)* | The field stays interactive-looking (closed state) while options load in the background; if opened before the fetch resolves, the open menu shows a "Loading options…" row (`../design-system/COMPONENTS.md § Select`) in place of the option list — never an empty-looking blank menu. Static, small option lists (Level, Content Language, the Language selector's English/العربية pair) never show this state — they render instantly. |

**Rule:** a skeleton always occupies the exact footprint of the content it stands in for, so nothing reflows when real content (or an empty/error state) arrives — per `../design-system/CONTENT_RESILIENCE.md § 3`.

## 2. Empty

**Component:** `EmptyState` (icon/illustration → title → description → primary action).

| Screen context | Copy pattern | Primary action |
|---|---|---|
| Explore — no results for search/filter | "No courses match your search" / "Try different keywords or clear your filters" | "Clear filters" |
| My Learning — no enrollments yet | "You haven't started a course yet" / "Explore Mentora's courses and start learning today" | "Explore Courses" |
| Certificates — none earned yet | "No certificates yet" / "Complete a course to earn your first certificate" | "Go to My Learning" |
| Learning Paths — none followed (My Learning context) | "You're not following a Learning Path yet" / "Browse curated paths to structure your learning" | "Browse Learning Paths" |
| Instructor Dashboard — no courses created | "You haven't created a course yet" / "Start building your first course" | "Create Course" |
| Course Editor — Curriculum — no sections yet | "This course has no content yet" / "Add your first section to get started" | "Add Section" |
| Admin lists — no matching results for a filter | "No results match your filters" | "Clear filters" |
| AI Tutor — new conversation | See § 10 (a lighter-weight variant, not a full `EmptyState`). |

## 3. Error

**Component:** `ErrorState` (icon → friendly title → plain-language description → retry action). Raw backend errors/status codes are never shown — this is a hard rule inherited from the design system, not a per-screen choice.

| Context | Treatment |
|---|---|
| Full content area failed to load (Explore, My Learning, Admin lists) | Full `ErrorState` replacing the content area, "Try again" retries the same request. |
| Single action failed (e.g. one course card's "Continue" tap) | `Snackbar` with a short message + inline retry, not a full-screen takeover — see § 5. |
| Form submission failed validation | Inline field-level error (`TextField` error state), not `ErrorState` — `ErrorState` is for *system* failures, not user input mistakes. |
| Video playback error | Inline error affordance sized to the video frame (icon + short message + retry) — a scaled-down `ErrorState`, per `COMPONENTS.md § Media & Playback`. |
| Demo Checkout simulated failure | See § 11 — a specific, product-approved copy variant, not generic `ErrorState` copy. |
| AI Tutor failed to respond | See § 10. |

## 4. Offline / Network Unavailable

Not a distinct DS component — composed from existing pieces:

- **Detection:** the client detects loss of connectivity (platform-native network-status API).
- **Treatment while offline:**
  - Any screen mid-load falls back to `ErrorState` with the specific copy "You're offline — check your connection and try again," retry re-attempts the request when tapped (does not auto-poll aggressively).
  - Already-loaded screens (e.g. mid-lesson in Course Player if video is already buffered/cached) are not forcibly interrupted — offline only blocks *new* requests (loading a new lesson, submitting a quiz, demo checkout).
  - A persistent, dismissible `Snackbar` ("You're offline") may surface once connectivity is lost, using the existing Snackbar spec — not a modal, since offline is a background condition, not a blocking one.
- **Recovery:** connectivity restored → the offline `Snackbar` (if shown) auto-dismisses; no forced page reload.

## 5. Inline / Transient Feedback

**Component:** `Snackbar`.

Used for: single-item action failures (§ 3), background sync failures ("Progress not saved — will retry"), non-critical confirmations that don't warrant a full state change (e.g. "Link copied"). Auto-dismisses after 4s, paused on hover/focus/touch, per `COMPONENTS.md § Dialogs, Sheets, Feedback`.

## 6. Success (Non-Transient)

**Component:** `SuccessState` (v1.2 — icon → title → description → primary action), distinct from the transient `Snackbar` above. Reserved for a genuine milestone moment, not routine confirmations.

| Context | Trigger |
|---|---|
| Demo Checkout | See § 11. |
| (Future, same pattern) Course completion | Could reuse `SuccessState` if a dedicated completion screen is ever introduced — MVP shows completion inline in Quiz Results/Course Player instead (see `SCREEN_UX_SPECS.md`), not as a separate screen. |

**Reduced motion:** `SuccessState`'s entrance animation respects `prefers-reduced-motion`/platform equivalent — cross-fade at `motion.duration.fast` instead of the scale/fade sequence, per `../design-system/ACCESSIBILITY.md § 9` and `COMPONENTS.md § SuccessState`. This is unconditional — never skipped for a "special" success moment.

## 7. Disabled

Not a standalone visual pattern — each interactive component's own Disabled state (`COMPONENTS.md`'s per-component tables) applies directly. Product-level rule: a control is disabled (not hidden) when the *reason* is informative to the user (e.g. "Submit Quiz" disabled until all questions answered; "Publish" disabled until a course meets minimum requirements) — hiding a control the user might reasonably expect to find is worse than showing it disabled with a reason.

**Select / Dropdown disabled options *(v1.3)*:** an individual option inside an open Select menu (not the whole field) can be disabled — e.g. a Category temporarily unavailable — per `../design-system/COMPONENTS.md § Select`'s per-option Disabled state. It renders dimmed, is skipped by keyboard arrow-key navigation, and is not selectable via tap/click, but stays visible in the list (not hidden) so its presence and unavailability are both communicated, consistent with the disabled-not-hidden rule above.

## 8. No Search Results

Specific instance of § 2 (Empty) — see the Explore row above. Applies identically to Admin list search (§ 2, Admin lists row).

## 9. No Enrolled Courses

Specific instance of § 2 — see the My Learning row above. This is the most likely *first-run* state a new Student sees, so its copy explicitly invites the next action (Explore) rather than just stating absence.

## 10. AI Tutor — Loading / Error / Empty

| State | Treatment |
|---|---|
| Empty conversation (first open) | Not a full `EmptyState` — a lightweight welcome message rendered as the first `AITutorBubble` ("Hi! Ask me anything about your courses, or try a quick action below.") plus the `AITutorQuickAction` row. |
| Thinking / awaiting response | An indeterminate loading indicator inside a pending `AITutorBubble` shape (three-dot pulse or equivalent, `color.brand.primary`) — not a full-screen spinner, since the rest of the conversation stays visible and scrollable. |
| Response received | Renders as a new `AITutorBubble`; long responses (see `SCREEN_UX_SPECS.md § AI Tutor`) wrap and grow the bubble, never truncate. |
| Failed to respond | The pending bubble is replaced by a compact inline error ("Something went wrong — try again") with a `TextButton` "Retry" inside the bubble slot — not a full `ErrorState`, since the surrounding conversation remains intact and useful. |

## 11. Demo Checkout — Processing / Success / Error

Cross-referenced in full from [`SCREEN_UX_SPECS.md § Demo Checkout`](./SCREEN_UX_SPECS.md) and `../product/DEMO_PAYMENT_FLOW.md`. Summary for state-language consistency:

- **Processing:** in-place on the Checkout screen — the `Checkout` component's confirm action (`PrimaryButton`) shows its own Loading state; the rest of the order summary stays static and visible. No route change during processing.
- **Success:** routes to `SuccessState` with the locked copy "Payment Successful" / "You're now enrolled!" and a "Start Learning" primary action — see § 6.
- **Error (optional, simulated):** inline on the Checkout screen, per `COMPONENTS.md § Checkout`'s Error state — "Demo checkout could not be completed. Try again." + "Try Again" `TextButton`. Never a card-decline-style message (no real payment processing exists to fail in that way).

---

## Cross-Reference Discipline

Every screen in [`SCREEN_UX_SPECS.md`](./SCREEN_UX_SPECS.md) names its Loading/Empty/Error/Success states by pointing to a section of this file (e.g. "Empty: § 2, My Learning row") rather than re-describing the pattern — if a screen's state genuinely needs custom copy or behavior beyond what's captured here, that copy is added as a **new row** in this file's tables, not invented inline in the screen spec. This keeps state behavior consistent across all 29 screens by construction, not by review.
