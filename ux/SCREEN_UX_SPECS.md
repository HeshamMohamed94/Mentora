# Mentora — Screen-Level UX Specifications

Implementation-ready UX specs for all **29 MVP screens**, matching [`../product/SCREEN_INVENTORY.md`](../product/SCREEN_INVENTORY.md) exactly — no screen added, none removed. Every component named is a [Mentora Design System v1.3.1](../design-system/COMPONENTS.md) component; every state references [`UX_STATES.md`](./UX_STATES.md); every responsive rule references [`RESPONSIVE_BEHAVIOR.md`](./RESPONSIVE_BEHAVIOR.md); every navigation fact references [`NAVIGATION_SPEC.md`](./NAVIGATION_SPEC.md). This file adds the *behavior and structure* those files don't already carry — it does not restate `SCREEN_INVENTORY.md`'s purpose/component list, it builds on top of them.

**Wireframes** (low-fidelity, structural only) are included for the 14 flagship screens the brief names (Course Player counts once, shown in both its Web and Mobile forms). All wireframes use the brief's own box-drawing format.

---

## A. Public / Pre-Auth

### 1. Landing

**Platform:** Web only · **Role:** Guest

**Entry points:** direct URL, external links, "Back" from any public page.
**Exit destinations:** Explore, Learning Paths, Course Details (via featured cards), Login, Register.
**Hierarchy:** root — see `NAVIGATION_SPEC.md § 1`.

**Header structure:** sticky public `Navbar` (logo · Explore · Learning Paths · Search · Login · Get Started).

**Main content, in order:**
1. Hero — headline, tagline ("Learn. Build. Grow."), sub-copy, `PrimaryButton` "Explore Courses" + `TonalButton`/`TextButton` "Get Started".
2. Popular/Featured Courses — 3–4 `CourseCard`s in a row.
3. Featured Learning Paths — 2–3 `LearningPathCard`s.
4. Brief "why Mentora" section (3 short value-prop blocks, icon + `heading.h4` + `body.small` each — no new component, composed from existing type/icon tokens).
5. Closing CTA band — repeat of "Get Started" for a page that scrolled past the hero.

**Primary CTA:** "Explore Courses" (hero). **Secondary:** "Get Started" (Register), "Login".

**Components:** Navbar, PrimaryButton, TonalButton, CourseCard, LearningPathCard.

**States:** Loading — featured content uses skeleton `CourseCard`s (`UX_STATES.md § 1`) if fetched dynamically; if seed/static, no loading state needed. Empty/Error — not applicable to a marketing page with static/seeded content in MVP.

**Responsive:** marketing max-width 1200–1280 (`RESPONSIVE_BEHAVIOR.md § 4`); hero stacks to single column on Mobile, CTA buttons stack full-width; featured rows drop to the course-grid column count per breakpoint (`§ 3`).

**Mobile differences:** Landing does not exist as a mobile-app screen — mobile opens directly to Login/Register or Explore-as-guest (`NAVIGATION_SPEC.md § 3`). This wireframe/spec is Web-only.

**RTL:** hero text/buttons mirror per logical properties; CTA button order (primary first) stays consistent, not physically fixed — see `../design-system/LOCALIZATION.md § 3`.

**Accessibility:** hero heading is a real `<h1>`; CTA buttons meet the 44px touch-target minimum; featured card grids are keyboard-navigable in reading order.

```
┌──────────────────────────────────────────┐
│ Navbar: Logo   Explore  Paths   Search  Login  [Get Started] │
├──────────────────────────────────────────┤
│              Learn. Build. Grow.          │
│         Short value-prop sentence         │
│      [ Explore Courses ]  [ Get Started ] │
├──────────────────────────────────────────┤
│ Popular Courses                           │
│ [Card] [Card] [Card] [Card]               │
├──────────────────────────────────────────┤
│ Featured Learning Paths                   │
│ [Path Card]   [Path Card]                 │
├──────────────────────────────────────────┤
│  Why Mentora — 3 short value props        │
├──────────────────────────────────────────┤
│           [ Get Started ]                 │
└──────────────────────────────────────────┘
```

---

### 2. Explore

**Platform:** Both · **Role:** Guest, Student

**Entry points:** Navbar/bottom-nav "Explore", Landing CTA, Sidebar "Explore".
**Exit destinations:** Course Details, Learning Paths (nested on Mobile).
**Hierarchy:** `NAVIGATION_SPEC.md §§ 1–3`.

**Header structure:** Web — Navbar/Sidebar + page title "Explore" (implicit, may be omitted in favor of the search bar as the visual header). Mobile — bottom-nav tab root, no page title needed (the tab bar's own "Explore" label suffices).

**Main content, in order:**
1. `SearchField`.
2. Filter row — `CategoryChip`s (category, level, price — a small fixed set per `../product/MVP_SCOPE.md`, not faceted search).
3. Result count (e.g. "48 courses") — quiet, `typography.caption`.
4. `CourseCard` grid, columns per `RESPONSIVE_BEHAVIOR.md § 3`.
5. (Mobile only) a segment/tab to Learning Paths — see `MOBILE_UX.md § 3`.

**Primary CTA:** none page-level (the CTA is per-card, "View"/tap-through). **Secondary:** filter chips, search.

**Components:** SearchField, CategoryChip, CourseCard, EmptyState, LoadingState, ErrorState.

**States:** Loading — skeleton grid, `UX_STATES.md § 1`. Empty — "No results" row, `§ 2` (Explore row), action "Clear filters". Error — full `ErrorState`, `§ 3`.

**Responsive:** grid 1/2/3/4 columns per breakpoint; filter row becomes horizontally scrollable rather than wrapping awkwardly at narrow widths.

**Mobile differences:** Learning Paths nested as a segment (not a separate destination); no persistent Sidebar.

**Web differences:** Sidebar (Student) or Navbar (Guest) always visible alongside content.

**RTL:** search icon and filter chip order mirror per logical properties; grid reading order (start→end, row by row) mirrors.

**Accessibility:** `SearchField` has a programmatic label even if visually represented by a placeholder+icon; filter chips are keyboard-focusable and announce selected state; result count changes are optionally announced via a polite live region so a screen-reader user knows filtering took effect.

```
┌──────────────────────────────────────────┐
│ [Sidebar/Navbar]  Explore                 │
│  [ Search courses...            🔍 ]      │
│  [Category ▾] [Level ▾] [Price ▾]         │
│  48 courses                               │
│  [Card] [Card] [Card] [Card]              │
│  [Card] [Card] [Card] [Card]              │
└──────────────────────────────────────────┘
```

---

### 3. Course Details

**Platform:** Both · **Role:** Guest, Student

**Entry points:** any `CourseCard` tap (Explore, My Learning, Learning Path Details, Home/Dashboard recommendations).
**Exit destinations:** Login/Register (enroll-gate, Guest), Demo Checkout (Student, not enrolled), Course Player (Student, enrolled).
**Hierarchy:** `NAVIGATION_SPEC.md §§ 1–3`.

**Header structure:** Web — Navbar/Sidebar. Mobile — pushed screen with a back affordance (system back / nav-bar back button), sticky primary CTA at the bottom of the viewport (`MOBILE_UX.md § 4`).

**Main content, in order:**
1. Thumbnail/preview (16:9).
2. Category `Badge`/`CategoryChip`.
3. Title (`heading.h1`/`h2`), instructor name (links to a summary, `InstructorCard`).
4. Rating, student count, duration — single meta row.
5. Price (or "Continue Learning" progress state if already enrolled — `ProgressBar`).
6. Primary CTA.
7. Description (body copy).
8. Curriculum outline — section/lesson titles, no video access (locked icon or simply no play affordance) until enrolled.
9. Instructor detail block (`InstructorCard`, expanded).

**Primary CTA:** "Enroll"/"Buy Course" (not enrolled) → Demo Checkout, or "Continue Learning" (enrolled) → Course Player. **Secondary:** none required in MVP (share is optional/future).

**Components:** CourseCard (hero variant), InstructorCard, CategoryChip, Badge, PrimaryButton, TonalButton, ProgressBar.

**States:** Loading — skeleton hero + text blocks, `§ 1`. Error — `ErrorState` if the course fails to load, `§ 3`. No Empty state (a course either exists or 404s, out of scope for a design-only phase).

**Responsive:** hero block stacks (thumbnail above meta on Mobile, side-by-side on Desktop where width allows); curriculum outline is always single-column, full width.

**Mobile differences:** sticky bottom CTA (§ above); back via system gesture, not a Navbar link.

**Web differences:** CTA is inline in the hero block, not sticky (page is short enough this is unnecessary at Desktop widths).

**RTL:** meta row (rating/count/duration icons) reorders start→end; curriculum list items follow logical text alignment.

**Accessibility:** curriculum outline uses a real list structure; locked/unavailable lesson previews are announced as such ("locked — enroll to access"), not silently unclickable.

```
┌──────────────────────────────────────────┐
│ [Navbar/Sidebar]                          │
│ [ Thumbnail 16:9 ]                        │
│ [Category chip]                           │
│ Course Title                              │
│ Instructor Name  ★4.8  1.2k students  6h  │
│ EGP 899                                   │
│ [ Enroll ]                                │
│ Description text...                       │
│ Curriculum                                │
│  ▸ Section 1 — 3 lessons                  │
│  ▸ Section 2 — 4 lessons                  │
│ Instructor detail block                   │
└──────────────────────────────────────────┘
```

---

### 4. Learning Paths (Explore)

**Platform:** Both · **Role:** Guest, Student

**Entry points:** Sidebar/Navbar "Learning Paths" (Web), Explore segment (Mobile).
**Exit destinations:** Learning Path Details.

**Header structure:** page title "Learning Paths" + Sidebar/Navbar (Web) or Explore-nested segment header (Mobile).

**Main content, in order:** 1. Intro line (optional, one sentence: "Curated, ordered course sequences"). 2. `LearningPathCard` grid/list.

**Primary CTA:** none page-level (per-card tap-through). **Secondary:** none.

**Components:** LearningPathCard, EmptyState, LoadingState.

**States:** Loading — skeleton `LearningPathCard`s, `§ 1`. Empty — unlikely (curated content always present), but if zero paths exist, a neutral "No Learning Paths yet" message, no CTA needed since browsing is already the context.

**Responsive:** grid follows the standard column rules; cards may be wider than `CourseCard` given their content (title + course-count + description) — a 2–3 column max even at Large Desktop is reasonable rather than 4, since path cards carry more text.

**Mobile differences:** nested under Explore, not a top-level destination.

**RTL:** card content start-aligned per logical properties.

**Accessibility:** each card's accessible name includes the path title and course count ("Android Developer, 6 courses").

---

### 5. Learning Path Details

**Platform:** Both · **Role:** Guest, Student

**Entry points:** Learning Paths grid, My Learning (followed paths).
**Exit destinations:** Course Details (per member course).

**Header structure:** hero block (path title/description) + Web Navbar/Sidebar or Mobile pushed-screen back affordance.

**Main content, in order:**
1. Path title, description.
2. Path-level `ProgressBar` (if following) — else a "Follow Path" `PrimaryButton`.
3. Ordered, numbered list of member `CourseCard`s, each annotated **Completed / Current / Upcoming** (see § below).
4. Each card's own enroll/continue affordance (same as Course Details' CTA logic, per-course).

**Course status annotation (Completed / Current / Upcoming):**
- **Completed** — `Badge` (success variant) + `check_circle` icon on the card.
- **Current** — the first not-yet-completed, enrolled-or-enrollable course; visually emphasized (e.g. a `border.focus`-colored outline or a "Continue here" label) — exactly one course holds this state at a time.
- **Upcoming** — remaining courses in sequence, no special badge, default `CourseCard` treatment (not locked — `../product/PRODUCT_SPEC.md § 12` allows out-of-order access in MVP, "Upcoming" is informational sequencing, not a gate).

**Primary CTA:** "Follow Path" (not following) or the Current course's own CTA (following). **Secondary:** none.

**Components:** LearningPathCard (header variant), CourseCard, ProgressBar, Badge, PrimaryButton.

**States:** Loading — skeleton, `§ 1`. Error — `ErrorState`, `§ 3`.

**Responsive:** member-course list is always single-column (numbered sequence reads better as a list than a grid, even on Desktop) — a deliberate exception to the standard course-grid, since sequence/order is the point of this screen.

**Mobile differences:** single scrolling column, same as Web (no layout fork needed here).

**RTL:** sequence numbers and connecting lines (if used visually) mirror their position but the *logical* order (1→2→3…) reads the same — numbering is never reversed.

**Accessibility:** each course's status (Completed/Current/Upcoming) is conveyed via icon + text + color together, never color alone (consistent with the design system's global rule).

```
┌──────────────────────────────────────────┐
│ Android Developer                         │
│ 6 courses · your path to Android dev      │
│ [██████░░░░] 2 of 6 complete              │
├──────────────────────────────────────────┤
│ ✓ 1. Kotlin Basics         Completed      │
│ ▶ 2. OOP                   Current        │
│   3. Coroutines            Upcoming       │
│   4. Jetpack Compose       Upcoming       │
│   5. Networking            Upcoming       │
│   6. Final Project         Upcoming       │
└──────────────────────────────────────────┘
```

---

### 6. Login

**Platform:** Both · **Role:** Guest

**Entry points:** Navbar/bottom-nav-adjacent "Login", any enroll-gate.
**Exit destinations:** role-appropriate landing (Dashboard/Home, Instructor Dashboard, Admin Dashboard), Register.

**Header structure:** minimal — logo/wordmark only, no Navbar link row (nothing to navigate to from a focused auth form).

**Main content, in order:** 1. "Log in to Mentora" title. 2. Email `TextField`. 3. Password `PasswordField`. 4. "Login" `PrimaryButton`. 5. "New to Mentora? Register" `TextButton`.

**Primary CTA:** "Login". **Secondary:** "Register" link.

**Components:** TextField, PasswordField, PrimaryButton, TextButton.

**States:** Loading — the "Login" button's own loading state (`UX_STATES.md § 1`). Error — inline, non-field-specific ("Incorrect email or password") for security, per `../product/USER_FLOWS.md § 2`.

**Responsive:** centered form card at form-width cap (`RESPONSIVE_BEHAVIOR.md § 4`) at every breakpoint; full-width fields on Mobile.

**Mobile differences:** presented outside the tab bar (`NAVIGATION_SPEC.md § 3`); keyboard-avoidance applies (`MOBILE_UX.md § 14`).

**RTL:** form fields and labels follow logical start alignment; the email field's *content* stays LTR-typed even inside an RTL form (`../design-system/LOCALIZATION.md § 3`, Forms).

**Accessibility:** labels programmatically associated with inputs (not placeholder-only); error is announced via a live region and focus moves to the first invalid field.

---

### 7. Register

**Platform:** Both · **Role:** Guest

**Entry points:** Navbar/bottom-nav "Get Started", Login's "Register" link, any enroll-gate.
**Exit destinations:** role-appropriate landing (always Student role for self-registration — Instructor/Admin accounts are provisioned outside self-serve registration, an implicit assumption carried from `../product/USER_ROLES.md`'s one-role-per-account model), Login.

**Header structure:** same minimal treatment as Login.

**Main content, in order:** 1. "Create your Mentora account" title. 2. Name `TextField`. 3. Email `TextField`. 4. Password `PasswordField` (with any strength hint as `caption` helper text, not a blocking rule beyond basic validation). 5. "Create Account" `PrimaryButton`. 6. "Already have an account? Login" `TextButton`.

**Primary CTA:** "Create Account". **Secondary:** "Login" link.

**Components:** TextField, PasswordField, PrimaryButton, TextButton.

**States:** Loading — button loading state. Error — inline field-level (e.g. "Email already registered" directly under the email field, since this one *is* actionable/specific, unlike Login's deliberately-vague error).

**Responsive/Mobile/RTL/Accessibility:** identical pattern to Login (§ 6) — same form-width cap, same keyboard-avoidance, same logical-properties alignment, same label-association rule.

---

## B. Student (Authenticated)

### 8. Dashboard / Home

**Platform:** Both (Web: "Dashboard"; Mobile: "Home") · **Role:** Student

**Entry points:** Login success, Sidebar "Dashboard" (Web) / Home tab (Mobile).
**Exit destinations:** Course Player, Explore, My Learning, AI Tutor.

**Header structure:** Web — Sidebar + greeting ("Welcome back, {name}"). Mobile — Home tab root, same greeting as the screen's de facto header.

**Main content, in order (exactly 3 modules — `WEB_UX.md § 5`):**
1. **Continue Learning** — one `CourseProgressCard` (the most recently active in-progress course) with its `ProgressBar` and a prominent "Continue" CTA. If no course in progress, this module is replaced by a single "Start your first course" prompt linking to Explore (not a separate Empty screen — this is a module-level empty state).
2. **Recommended / Next Courses** — a short row (3–4) of `CourseCard`s, pulled from the student's enrolled Learning Paths' next course or general catalog highlights.
3. **Learning Paths in progress + quick stats** — followed `LearningPathCard`(s) with path-level progress, plus a small `StatCard` row (courses in progress, completed, certificates earned — 3 cards).

**Primary CTA:** "Continue" (module 1). **Secondary:** module 2/3 card taps.

**Components:** CourseProgressCard, StatCard, LearningPathCard, CourseCard, ProgressBar.

**States:** Loading — skeletons per module (`§ 1`). Empty — new-student state described in module 1 above; modules 2–3 degrade gracefully (module 2 always has content since it's catalog-driven; module 3 hides entirely if no paths are followed, rather than showing an empty `LearningPathCard` slot).

**Responsive:** modules stack vertically at every breakpoint; module 2's course row uses the standard course-grid column count.

**Mobile differences:** no Sidebar; greeting sits directly under the (hidden, since it's a tab root) header.

**Web differences:** Sidebar always visible.

**RTL:** greeting and module titles start-aligned; "Continue" CTA position follows logical end (per button-order convention, `../design-system/LOCALIZATION.md`).

**Accessibility:** module headings are real headings (`h2`-equivalent) so screen-reader users can jump between modules; the Continue Learning module's progress is announced with both percentage and a text label ("62% complete"), not a bare visual bar.

```
┌──────────────────────────────────────────┐
│ [Sidebar]  Welcome back, Sarah            │
│                                            │
│ Continue Learning                         │
│ [ Course thumb | Title | ██████░░ 62% | Continue ] │
│                                            │
│ Recommended for you                       │
│ [Card] [Card] [Card]                      │
│                                            │
│ Your Learning Paths            [Stats row]│
│ [Path Card — 2/6 complete]  [3][1][2]     │
└──────────────────────────────────────────┘
```

---

### 9. My Learning

**Platform:** Both · **Role:** Student

**Entry points:** Sidebar "My Learning" (Web), bottom-nav "My Learning" (Mobile), Dashboard/Home.
**Exit destinations:** Course Player, Learning Path Details, Certificates (nested, Mobile).

**Header structure:** page title "My Learning" + a status filter (All / In Progress / Completed).

**Main content, in order:**
1. Status filter row.
2. `CourseProgressCard` grid/list (filtered).
3. Followed Learning Paths section (path-level progress).
4. (Mobile) entry point into Certificates, nested here per `../product/INFORMATION_ARCHITECTURE.md § 3`.

**Primary CTA:** "Continue"/"Review" per card. **Secondary:** status filter.

**Components:** CourseProgressCard, LearningPathCard, ProgressBar, EmptyState.

**States:** Loading — skeleton grid, `§ 1`. Empty — "no enrollments yet," `§ 2` (My Learning row), CTA "Explore Courses."

**Responsive:** standard course-grid columns; on Mobile, the filter row is a simple 3-segment control near the top rather than chips (a small, deliberate variant — functionally identical to `CategoryChip` selection behavior, no new component needed).

**Mobile differences:** Certificates entry point lives here (§ header structure).

**Web differences:** Certificates has its own Sidebar item, so no nested entry point is needed here.

**RTL:** filter segments and card grid mirror per logical properties.

**Accessibility:** the active filter is announced (`aria-pressed`/equivalent) and the result set change is conveyed via a polite live region, matching Explore's pattern.

```
┌──────────────────────────────────────────┐
│ My Learning                               │
│ [All] [In Progress] [Completed]           │
│ [Progress Card] [Progress Card]           │
│ [Progress Card] [Progress Card]           │
│                                            │
│ Your Learning Paths                       │
│ [Path Card — 2/6 complete]                │
└──────────────────────────────────────────┘
```

---

### 10. Course Player

**Platform:** Both · **Role:** Student

**Entry points:** "Start Learning" (post-purchase), "Continue" (My Learning/Dashboard), Course Details "Continue Learning."
**Exit destinations:** Quiz (automatic, last lesson), AI Tutor (contextual), My Learning (back).

**Header structure:** Web — reduced-chrome (Sidebar collapsed by default, `WEB_UX.md § 3`), a slim top bar with course title + close/back affordance. Mobile — full-screen, bottom nav hidden (`MOBILE_UX.md § 6`), back affordance in the (temporarily visible on tap) player chrome or a persistent small back control above the video.

**Main content, in order:**

*Web (two-column):*
1. **Left/primary column (~70%):** `VideoPlayer` (16:9) → lesson title → lesson description → tab row (**Overview | Resources** — exactly these two, no Notes/Discussion, both are MVP; see § below) → tab content → Previous/Next lesson controls → "Mark Complete" (if not auto-triggered by video end) → AI Tutor entry affordance.
2. **Right column (~30%, persistent sidebar):** course-level `ProgressBar` + section/lesson list with per-lesson completion `Badge`, current lesson highlighted.

*Mobile (single column):*
1. `VideoPlayer` (16:9, full width) → lesson title → short description → Previous/Next lesson controls → "Mark Complete" → tab row (Overview | Resources) → tab content → AI Tutor entry affordance.
2. **Curriculum** is not inline — reached via a "Curriculum" affordance that opens a `BottomSheet` (`MOBILE_UX.md §§ 6–7`), containing the same section/lesson list Web shows persistently.

**Tabs — MVP scope confirmed:** only **Overview** (lesson description, already shown above the fold — the tab may simply re-surface it or hold slightly extended notes if the description is long) and **Resources** (lesson resource links, per `../product/PRODUCT_SPEC.md § 14`). **Notes and Discussion are explicitly not built** — both are Post-MVP (`../product/MVP_SCOPE.md`); introducing them here would leak Post-MVP scope into an MVP screen.

**Primary CTA:** "Mark Complete" / auto-advance on video end. **Secondary:** Previous/Next lesson, AI Tutor entry, Curriculum (Mobile).

**Components:** VideoPlayer/PlaybackControls, ProgressBar, Badge, Tabs, BottomSheet (Mobile curriculum), AITutorQuickAction (entry point).

**States:** Loading — video skeleton at 16:9 (`§ 1`) while metadata/video source loads. Error — inline error sized to the video frame, retry (`§ 3`, Video playback error row). Success — course completion is **not** a separate state of this screen; it routes into Quiz (if present) or directly surfaces a completion confirmation inline (see Quiz Results, screen 12, and `../product/USER_FLOWS.md § 17`).

**Responsive:** the defining responsive behavior of this screen — see `RESPONSIVE_BEHAVIOR.md § 7` for the full breakpoint table (two-column ≥ Desktop, single-column + Bottom Sheet < Desktop).

**Mobile differences:** Curriculum Bottom Sheet instead of persistent sidebar (the single largest platform-specific divergence in the whole app, and an explicitly sanctioned one).

**Web differences:** persistent curriculum sidebar; Sidebar nav chrome collapses to maximize width.

**RTL:** the `VideoPlayer` scrubber/timeline **never mirrors**, regardless of app layout direction (`../design-system/LOCALIZATION.md § 3`, `COMPONENTS.md § Media & Playback`) — the one locked exception in the entire product. Everything else (curriculum list, tabs, Previous/Next control positions) mirrors normally.

**Accessibility:** every `VideoPlayer` control has a text label (`../design-system/ACCESSIBILITY.md § 14`); the current lesson is announced when the player advances; the Curriculum Bottom Sheet's lesson rows carry their completion state as text, not color alone.

```
Web:
┌──────────────────────────────┬───────────┐
│ [ VideoPlayer 16:9        ]  │ Progress  │
│ Lesson Title                 │ ██████░░  │
│ Overview | Resources         │ ▸ Sec 1   │
│  Lesson description...       │  ✓ L1     │
│                               │  ▶ L2     │
│ [ Prev ]  [ Mark Complete ]  [Next]│  Sec 2  │
└──────────────────────────────┴───────────┘

Mobile:
┌──────────────────────────────┐
│ [ VideoPlayer 16:9         ] │
│ Lesson Title                 │
│ [ Prev ]   [ Mark Complete ] [Next]│
│ Overview | Resources         │
│  Lesson description...       │
│ [ ▤ Curriculum ]             │
└──────────────────────────────┘
   ⌄ (tap Curriculum) ⌄
┌──────────────────────────────┐
│ ▔▔ (drag handle) ▔▔          │
│ Section 1                    │
│  ✓ Lesson 1                  │
│  ▶ Lesson 2  (current)       │
│ Section 2                    │
│   Lesson 3                   │
└──────────────────────────────┘
```

---

### 11. Quiz

**Platform:** Both · **Role:** Student

**Entry points:** automatic after the last lesson of a course with a quiz; "Take Quiz" from My Learning/Course Player if revisiting.
**Exit destinations:** Quiz Results.

**Header structure:** minimal chrome (bottom nav hidden on Mobile, Sidebar collapsed on Web) — a focused, single-task surface. No quiz-intro screen in MVP (kept minimal per this task's instruction to avoid unnecessary screens) — the first question renders immediately, with the question-progress indicator itself communicating "Question 1 of N" as sufficient framing.

**Main content, in order:** 1. Question-progress indicator ("Question 3 of 10" + slim `ProgressBar`). 2. `QuizCard` question text. 3. Answer options (full-width rows, per `../design-system/COMPONENTS.md § Quiz System`). 4. "Next" (or "Submit Quiz" on the final question).

**Primary CTA:** "Next" / "Submit Quiz". **Secondary:** none (no skip, no back-to-previous-question control in MVP — answers already selected are preserved if the student navigates away and returns, per `../product/USER_FLOWS.md § 14`, but there is no explicit "Previous" button, keeping the flow deliberately linear/simple).

**Components:** QuizCard, ProgressBar, PrimaryButton.

**States:** Loading — question content skeleton if fetched per-question (unlikely necessary if the whole quiz loads at once, but specified for completeness, `§ 1`). Error — `ErrorState` if the quiz fails to load. No Empty state (a course with no quiz never reaches this screen).

**Responsive:** single-column at every breakpoint — a quiz question never benefits from a wide multi-column layout; content simply gets more side-margin at wider viewports (form-width cap logic, `RESPONSIVE_BEHAVIOR.md § 4`).

**Mobile differences:** "Next"/"Submit" pinned at the bottom of the viewport for thumb reach.

**RTL:** answer option text start-aligned; selection/correct/incorrect icons stay in their fixed semantic position (leading the text) regardless of direction, mirrored per logical properties.

**Accessibility:** each answer option is a real radio-group member (single-select, keyboard arrow-navigable); "Next"/"Submit" is disabled with an explained reason (per `UX_STATES.md § 7`) until an answer is selected.

```
┌──────────────────────────────┐
│ Question 3 of 10   ████░░░░  │
│                               │
│ What does OOP stand for?     │
│ ○ Object-Oriented Programming│
│ ○ Open Operating Protocol    │
│ ○ Optimal Output Process     │
│                               │
│              [ Next ]        │
└──────────────────────────────┘
```

---

### 12. Quiz Results

**Platform:** Both · **Role:** Student

**Entry points:** automatic, immediately after Quiz submission.
**Exit destinations:** Course Player (Retry, if failed) / My Learning or Certificate Detail (Continue, if passed and course completes).

**Header structure:** same minimal chrome as Quiz.

**Main content, in order:**
1. Score summary — large `StatCard`-style number ("8/10") + pass/fail `Badge`.
2. Per-question breakdown — each question with icon + label + color (`check_circle`/"Correct"/success, or `cancel`/"Incorrect"/error) per the design system's locked "never color-only" rule.
3. If passed **and** this was the course's last completion condition — an inline **course completion confirmation** (not a separate screen, per `../product/USER_FLOWS.md § 17`): a short success-styled banner/section ("🎉 Course Completed!") with a "View Certificate" `PrimaryButton`, composed using the same `success.*` tokens as `SuccessState` but embedded within this screen rather than a full-screen takeover (since the quiz results themselves remain relevant context).
4. Continue/Retry action.

**Primary CTA:** "Continue" (passed) or "Retry Quiz" (failed). **Secondary:** "View Certificate" (if the completion banner is shown).

**Components:** StatCard, Badge, PrimaryButton, TonalButton.

**States:** no Loading/Empty/Error beyond the standard submit-failure Snackbar (`UX_STATES.md § 5`) if the submission itself fails to save — retried transparently, not a blocking error screen for a transient save failure.

**Responsive:** single column, form-width cap.

**Mobile/Web:** identical layout — no platform fork needed for this screen.

**RTL:** correctness icon leads the question text per logical start, mirrored consistently.

**Accessibility:** the score is announced as text ("8 out of 10, passed") not just visually; each question's correct/incorrect state is in the accessible name of its row, not conveyed by icon color alone (already covered by the icon+text+color rule, restated here as it is one of the most safety-critical instances of it in the product).

---

### 13. Certificates List

**Platform:** Both · **Role:** Student

**Entry points:** Sidebar "Certificates" (Web), My Learning-nested (Mobile).
**Exit destinations:** Certificate Detail.

**Header structure:** page title "Certificates".

**Main content, in order:** 1. `CertificateCard` grid.

**Primary CTA:** none page-level (per-card tap). **Secondary:** none.

**Components:** CertificateCard, EmptyState.

**States:** Loading — skeleton grid. Empty — "no certificates yet," `§ 2` (Certificates row), CTA "Go to My Learning".

**Responsive:** standard grid columns, though certificate cards may cap at 2–3 columns even at Large Desktop (they carry more visual weight than a `CourseCard`) — a layout judgment, not a token change.

**RTL:** card content mirrors per logical properties.

**Accessibility:** each card's accessible name includes course title and completion date.

---

### 14. Certificate Detail

**Platform:** Both · **Role:** Student

**Entry points:** Certificates list, the Quiz Results completion banner (§ 12).
**Exit destinations:** none forward (terminal); back to Certificates.

**Header structure:** minimal chrome, back affordance.

**Main content, in order:** 1. Full certificate rendering (student name, course title, instructor, completion date, Mentora certificate identifier — `../product/PRODUCT_SPEC.md § 13`). 2. "Share" action (native share sheet on Mobile / a styled share affordance on Web — UI-only, no real LinkedIn API call, per `../product/PRODUCT_SPEC.md § 13`).

**Primary CTA:** "Share". **Secondary:** "Download"/"View full size" if included.

**Components:** CertificateCard (expanded/detail variant), PrimaryButton, TextButton.

**States:** Loading — skeleton. Error — `ErrorState` if the certificate fails to render/load.

**Responsive:** the certificate artifact itself is a fixed-aspect visual (like a document) — centers and scales down on narrow viewports without cropping, never stretches to fill the viewport unnaturally wide on Desktop.

**RTL:** the certificate's *own* content (name, course title) follows the certificate's authored language direction (which may be LTR regardless of the app's current UI direction, since a certificate is a fixed artifact) — the surrounding chrome (Share button, back affordance) follows normal app RTL rules. This is a deliberate, narrow exception similar in spirit to the VideoPlayer scrubber rule.

**Accessibility:** the certificate's text content (name, course, date) is available to screen readers as real text, not only as an image — even if the visual rendering is image-based, an accessible text equivalent is provided.

---

### 15. AI Tutor

**Platform:** Both · **Role:** Student

**Entry points:** Sidebar "AI Tutor" (Web) / bottom-nav "AI Tutor" (Mobile) — **global**; AI Tutor affordance inside Course Player — **lesson-context**.
**Exit destinations:** none forward (self-contained); back to wherever it was opened from.

**Header structure:** minimal — "AI Tutor" title/branding, no competing navigation chrome beyond the persistent Sidebar (Web Tablet+) or the fact that it's a bottom-nav tab (Mobile).

**Main content, in order:**
1. Chat thread — `AITutorBubble` messages (AI on `surface.variant`, Student on `brand.primaryContainer`, per `../design-system/COMPONENTS.md § AI Tutor`). First-open shows the lightweight welcome message (`UX_STATES.md § 10`), not empty white space.
2. Quick-action row — `AITutorQuickAction` chips (*Explain this lesson, Summarize, Give me an example, Quiz me, What should I learn next?*), horizontally scrollable, pinned just above the input.
3. Message input (`TextField` variant) + send action.

**Primary CTA:** send a message / tap a quick action. **Secondary:** scroll chat history.

**Components:** AITutorBubble, AITutorQuickAction, TextField, LoadingState (thinking indicator).

**Quick-action behavior:** tapping a quick action inserts/sends a corresponding prompt immediately (no extra confirmation step) and shows the "thinking" state (`UX_STATES.md § 10`) exactly as a typed message would. When opened with lesson context (from Course Player), quick actions act on that lesson automatically; opened globally, "Explain this lesson"/"Summarize" fall back to asking the student which course/lesson they mean (a clarifying AI response, not a UI-level error) since no lesson context exists.

**Long response behavior:** `AITutorBubble` wraps and grows vertically without limit or truncation (`../design-system/CONTENT_RESILIENCE.md § 1`) — the chat thread scrolls, the bubble never scrolls internally or clips.

**Mobile keyboard behavior:** input pinned above the on-screen keyboard; sending a message keeps the keyboard open (`MOBILE_UX.md § 14`).

**States:** Empty (first open) — welcome message, `§ 10`. Loading (thinking) — `§ 10`. Error (failed response) — inline retry within the bubble slot, `§ 10`. Success — not applicable as a distinct state (every successful response is just a new bubble, not a special "success" moment).

**Responsive:** full-screen (Mobile) vs. full-height panel (Web, Sidebar still visible) — `RESPONSIVE_BEHAVIOR.md § 9`. Bubble max-width caps for readability at wide viewports.

**RTL:** AI bubble tail direction mirrors (bottom-start for AI, bottom-end for Student — swapped, not both-same, per `../design-system/COMPONENTS.md § AI Tutor`); quick-action chip order mirrors.

**Accessibility:** new AI messages announce via a live region (`../design-system/ACCESSIBILITY.md § 5`); quick-action chips are individually focusable and labeled with their full action text, not abbreviated.

```
┌──────────────────────────────┐
│ AI Tutor                     │
│                               │
│ [AI] Hi! Ask me anything...  │
│           [You] What's OOP?  │
│ [AI] OOP stands for...       │
│                               │
│ [Explain][Summarize][Example]│
│ [ Ask a question...    ➤ ]  │
└──────────────────────────────┘
```

---

### 16. Profile

**Platform:** Both · **Role:** Student

**Entry points:** Sidebar "Profile" (Web) / bottom-nav "Profile" (Mobile).
**Exit destinations:** Settings.

**Header structure:** page title "Profile".

**Main content, in order:** 1. `Avatar` (large) + name + email. 2. Basic stats (courses completed, certificates earned). 3. "Edit Profile" action. 4. "Settings" entry. 5. "Logout".

**Primary CTA:** "Edit Profile". **Secondary:** "Settings", "Logout".

**Components:** Avatar, TextField (edit mode), PrimaryButton, TextButton.

**States:** Loading — skeleton. Error — `ErrorState` on save failure (inline field errors preferred where applicable, per the input error pattern).

**Responsive:** single centered column at every breakpoint (`WEB_UX.md § 3`).

**RTL:** avatar/name block stays start-aligned; edit form follows the standard form RTL rules.

**Accessibility:** avatar has an accessible name ("Sarah's profile photo" or initials-fallback equivalent per `../design-system/COMPONENTS.md § Avatar`).

---

### 17. Settings

**Platform:** Both · **Role:** Student

**Entry points:** Profile "Settings" (Instructor/Admin reach the same language control inside their own Profile/Account equivalent, not a separate Settings screen — see `INSTRUCTOR_ADMIN_UX.md`).
**Exit destinations:** none forward (terminal); back to Profile.

**Header structure:** page title "Settings", back affordance.

**Main content, in order:** 1. Account/password change fields. 2. **Language *(v1.3, locked MVP)*** — a functional `Select` (DS v1.3) with two options, English and العربية (native-script labels, not "Arabic"/"English" translated into the other language); shows the currently active language as the selected value; changing it applies immediately (see Behavior below) — never hidden, never a "Coming Soon" placeholder. 3. Logout.

**Language field behavior:** selecting a new language (a) swaps all UI strings on screen and across the app to that language, (b) flips layout direction (LTR↔RTL) app-wide, (c) requires no navigation away from Settings and no app restart — the change is visible the instant the Select closes. No confirmation dialog; the action is instantly reversible by reopening the Select. Guest and every authenticated role get the identical control and behavior (`../product/PRODUCT_SPEC.md § 16`). Persistence is conceptual only at this planning stage: a signed-in user's choice is understood to also save to their account for cross-device consistency; a Guest's choice is understood to save locally on-device only — no storage mechanism is implemented here (Technical Architecture).

**Primary CTA:** "Save Changes" (applies to account/password fields; the Language Select applies on selection, independent of this button). **Secondary:** "Logout".

**Components:** TextField, Select (DS v1.3, Language), PrimaryButton, TextButton.

**States:** Loading — button loading state on save; "Loading options…" is not expected for the 2-item Language Select (static list) but the Select component's loading-options state exists in the design system for consistency (`../design-system/COMPONENTS.md § Select`). Error — inline field errors.

**Responsive/RTL/Accessibility:** identical pattern to Profile (§ 16). The Language Select itself follows `UX_STATES.md § Select` and `../design-system/ACCESSIBILITY.md § 14` (full keyboard operability, ARIA combobox/listbox semantics on Web, native picker mapping on Android/iOS); its own option list is never mirrored regardless of the *current* language, since option order (English, then العربية) is a fixed convention, not directional content.

---

### 18. Demo Checkout

**Platform:** Both · **Role:** Student

**Entry points:** Course Details "Enroll"/"Buy Course" (authenticated).
**Exit destinations:** Purchase Success (success), Course Details (cancel).

**Header structure:** minimal chrome, "Checkout" or the course title as the effective header.

**Main content, in order (closed field set — `../design-system/COMPONENTS.md § Checkout`, `../product/DEMO_PAYMENT_FLOW.md`):**
1. Course line item — thumbnail, title, instructor.
2. Price row(s) — demo price (and original price if used for a discount presentation, per `../product/DEMO_PAYMENT_FLOW.md § 5`).
3. Order total.
4. **Demo-payment notice** — "Demo Payment — no real charges or payment information required." (`color.info.container`/`onInfoContainer`).
5. "Complete Demo Purchase" `PrimaryButton`.
6. "Cancel" back to Course Details.

**Explicitly never present:** card number, CVV, expiry, billing address, bank information, or any payment-provider logo implying a real integration — this is a closed, exhaustive field list, not a starting point to extend.

**Primary CTA:** "Complete Demo Purchase". **Secondary:** "Cancel".

**Components:** Checkout/OrderSummary, PrimaryButton, TextButton.

**Interaction:** Complete Demo Purchase → in-place processing state (confirm button's own Loading state, rest of summary static, `UX_STATES.md § 11`) → routes to Purchase Success on completion → enrollment created server-side (conceptually — no backend exists yet in this planning phase).

**States:** Processing — `§ 11`. Error (optional, simulated) — inline "Demo checkout could not be completed. Try again." + "Try Again", per `§ 11` and `../design-system/COMPONENTS.md § Checkout`'s Error state.

**Responsive:** single-column card at form-width cap, every breakpoint (`RESPONSIVE_BEHAVIOR.md § 10`).

**RTL:** notice text and price rows follow logical start/end; total amount stays a fixed numeral format (Western Arabic numerals per the locked decision in `../design-system/LOCALIZATION.md § 7`) regardless of locale.

**Accessibility:** the demo-payment notice is programmatically associated with the confirm action's context (`../design-system/ACCESSIBILITY.md § 14`) so it's heard, not just seen.

```
┌──────────────────────────────┐
│ Checkout                     │
│ [thumb] Kotlin Basics        │
│         by Jane Doe          │
│                               │
│ Course price      EGP 899    │
│ Total              EGP 899   │
│                               │
│ ℹ Demo Payment — no real     │
│   charges or payment info    │
│   required.                  │
│                               │
│ [ Complete Demo Purchase ]   │
│ [ Cancel ]                   │
└──────────────────────────────┘
```

---

### 19. Purchase Success

**Platform:** Both · **Role:** Student

**Entry points:** automatic, on successful Demo Checkout.
**Exit destinations:** Course Player ("Start Learning"), My Learning.

**Header structure:** none — full-bleed celebratory moment, minimal/no chrome.

**Main content, in order:** 1. Success icon/illustration (`color.success.default`), animated entrance (respecting reduced motion, `../design-system/COMPONENTS.md § SuccessState`). 2. "Payment Successful" (title). 3. "You're now enrolled!" (description). 4. "Start Learning" `PrimaryButton`. 5. "Back to My Learning" `TextButton`.

**Primary CTA:** "Start Learning". **Secondary:** "Back to My Learning".

**Components:** SuccessState.

**States:** entrance motion — `motion.duration.slow` + `easing.decelerate`, or an instant cross-fade under reduced motion (`../design-system/ACCESSIBILITY.md § 9`) — unconditionally, even for this portfolio-priority moment.

**Responsive:** centered content, single column, every breakpoint.

**Navigation:** back (browser/system) is disabled/redirected to My Learning rather than re-entering the completed checkout (`NAVIGATION_SPEC.md § 6`).

**RTL:** text content mirrors per logical properties; the success iconography itself (a checkmark) is non-directional and never mirrors.

**Accessibility:** the success message is announced via a live region on appearance (`../design-system/ACCESSIBILITY.md § 14`, `UX_STATES.md § 6`) so a screen-reader user gets the same confirmation a sighted user gets from the animation.

```
┌──────────────────────────────┐
│                               │
│            ✓ (animated)      │
│      Payment Successful      │
│    You're now enrolled!      │
│                               │
│      [ Start Learning ]      │
│    [ Back to My Learning ]   │
│                               │
└──────────────────────────────┘
```

---

## C. Instructor (Web-only)

### 20. Instructor Dashboard

**Platform:** Web · **Role:** Instructor

**Entry points:** Login success (Instructor role).
**Exit destinations:** Course Editor — Overview (new or existing course).

**Header structure:** Instructor Sidebar (Dashboard, My Courses, Profile) + page title "Dashboard".

**Main content, in order:** 1. `StatCard` row — total courses, published count, total enrollments (3 cards max, `INSTRUCTOR_ADMIN_UX.md § 1`). 2. "Create Course" `PrimaryButton`. 3. Course list (`DataTable`: title, status `Badge`, enrollment count, completion rate, row action).

**Primary CTA:** "Create Course". **Secondary:** open an existing course from the list.

**Components:** StatCard, Badge, DataTable, PrimaryButton.

**States:** Loading — skeleton stat cards + skeleton table rows (`UX_STATES.md § 1`). Empty — "you haven't created a course yet," `§ 2` (Instructor Dashboard row), CTA "Create Course". Error — `ErrorState` with retry.

**Responsive:** `DataTable` collapses to stacked cards below Desktop (`RESPONSIVE_BEHAVIOR.md § 6`); stat cards stack to 1–2 per row on narrow viewports.

**Web differences:** N/A (Web-only screen).

**RTL:** table headers/columns and row content mirror per logical properties; status badge stays leading the row's title per logical start.

**Accessibility:** `DataTable` uses real table semantics with `<th scope>`/equivalent; the collapsed-card view preserves the same label:value semantic pairing (`../design-system/ACCESSIBILITY.md § 14`).

```
┌──────────────────────────────────────────┐
│ [Sidebar] Dashboard                       │
│ [12 courses] [8 published] [1.2k enrolled]│
│ [ + Create Course ]                       │
│ ─────────────────────────────────────────│
│ Title            Status   Enroll  Compl. │
│ Kotlin Basics     Published  340    72%  │
│ Advanced Kotlin   Draft        0     —   │
└──────────────────────────────────────────┘
```

---

### 21. Course Editor — Overview

**Platform:** Web · **Role:** Instructor

**Entry points:** Instructor Dashboard "Create Course" (new) / row tap (existing).
**Exit destinations:** Course Editor — Curriculum (tab), Instructor Dashboard (back/save).

**Header structure:** Tabs — **Overview** (active) | **Curriculum**.

**Main content, in order (`INSTRUCTOR_ADMIN_UX.md § 1`):**
1. Title `TextField`.
2. Description `TextField` (multiline).
3. Category `Select` (DS v1.3 — real component; the earlier chip-based stopgap is retired, see § 4 below).
4. **Level `Select` *(v1.3, new field)*** — Beginner / Intermediate / Advanced.
5. **Content Language `Select` *(v1.3, new field)*** — the language the course content (video, lesson text, resources) is authored/recorded in; explicitly independent of the instructor's own UI language and of any student's UI language (`../product/PRODUCT_SPEC.md § 16`). No automatic translation is implied or offered here.
6. Price `TextField` (demo pricing).
7. Thumbnail `FileUpload`.
8. Publish-readiness checklist (met/unmet requirements).
9. Publish/Unpublish `Toggle` — labeled "Draft"/"Published" as a visible text label beside the switch at all times, disabled until the checklist passes. Per the approved UX decision (`NAVIGATION_SPEC.md`), state is never conveyed by switch color/position alone.

**Primary CTA:** "Save" (metadata) and, once ready, "Publish". **Secondary:** "Unpublish" (if already published), navigate to Curriculum tab.

**Components:** TextField, FileUpload, Select (DS v1.3 — Category, Level, Content Language), Toggle, Badge (checklist items), PrimaryButton, TonalButton, Tabs.

**States:** Loading — skeleton form; each Select independently supports a "Loading options…" state if its option list is fetched (e.g. Category) rather than static (e.g. Level). Error — inline field validation; a save failure shows a `Snackbar` retry (`UX_STATES.md § 5`), not a full-screen error (the form content is still valid/present). Success — a `Snackbar` confirmation on save ("Course saved") — not a full `SuccessState` (routine, not a milestone).

**Responsive:** form fields stack full-width below Desktop; `DataTable`/table concerns don't apply to this screen.

**RTL:** form labels/fields start-aligned; the FileUpload dropzone's icon/text follow logical alignment; each Select's menu opens start-aligned to its field and its trailing `expand_more`/`expand_less` indicator sits at the field's logical end.

**Accessibility:** the publish-readiness checklist items are read as a real list with pass/fail state conveyed via icon+text+color; the disabled Publish control's reason is available to assistive tech (not just a visual dim), per `UX_STATES.md § 7`; the Publish/Unpublish Toggle's accessible name includes the current state text ("Published" / "Draft"), not just "toggle."

```
┌──────────────────────────────────────────┐
│ Overview | Curriculum                     │
│ Title        [ Kotlin Basics          ]   │
│ Description  [ ...                    ]   │
│ Category [ Programming ▾ ]  Level [ Beg ▾ ]│
│ Content Lang [ English ▾ ]  Price [ 899 ]  │
│ Thumbnail    [ Drag file or Browse    ]   │
│ ✓ Title set   ✓ Category set              │
│ ✗ At least one lesson with video          │
│ [ Save ]   Draft  ○────  (disabled)       │
└──────────────────────────────────────────┘
```

---

### 22. Course Editor — Curriculum

**Platform:** Web · **Role:** Instructor

**Entry points:** Course Editor — Overview's "Curriculum" tab.
**Exit destinations:** Lesson Editor, Quiz Editor.

**Header structure:** same Tabs row, **Curriculum** active.

**Main content, in order:** 1. "Add Section" `PrimaryButton`. 2. `ReorderableList` of sections, each expandable to its own nested `ReorderableList` of lessons (with per-section "Add Lesson"). 3. "Add Quiz" (course-level, at the bottom of the curriculum).

**Primary CTA:** "Add Section" / "Add Lesson" (contextual). **Secondary:** reorder, delete, "Add Quiz"/edit existing quiz.

**Components:** ReorderableList/DragHandle, Badge (video-attached indicator), TonalButton, TextButton, IconButton.

**States:** Loading — skeleton list. Empty — "this course has no content yet," `§ 2` (Course Editor row), CTA "Add Section". Error — `Snackbar` on a failed reorder/save (retried transparently where possible).

**Reordering — accessible alternative required:** every section and lesson row exposes Move Up/Move Down `IconButton`s in addition to the drag handle, always visible — not drag-only (`../design-system/COMPONENTS.md § ReorderableList`, `../design-system/ACCESSIBILITY.md § 14`).

**Responsive:** list stacks full-width below Desktop; nested lesson lists remain legible (indentation reduces but doesn't disappear).

**RTL:** drag handle and Move Up/Down icons are non-directional (vertical), unaffected by RTL; row content (title, badges) mirrors per logical properties.

**Accessibility:** each reorder action (drag or button) announces the new position ("Lesson 2 moved to position 1 of 3") via a live region.

```
┌──────────────────────────────────────────┐
│ Overview | Curriculum                     │
│ [ + Add Section ]                         │
│ ⠿ Section 1: Getting Started    [↑][↓][🗑]│
│    ⠿ Lesson 1: Intro   ✓video  [↑][↓][🗑] │
│    ⠿ Lesson 2: Setup   ✗video  [↑][↓][🗑] │
│    [ + Add Lesson ]                       │
│ ⠿ Section 2: Core Concepts      [↑][↓][🗑]│
│ [ + Add Quiz ]                            │
└──────────────────────────────────────────┘
```

---

### 23. Lesson Editor

**Platform:** Web · **Role:** Instructor

**Entry points:** Curriculum "Add Lesson" / edit an existing lesson row.
**Exit destinations:** Course Editor — Curriculum (Save/Cancel/Delete).

**Header structure:** minimal — "Edit Lesson" / "New Lesson" title, Save/Cancel.

**Main content, in order:** 1. Title `TextField`. 2. Description `TextField` (multiline). 3. Video `FileUpload` (Idle → Uploading with `ProgressBar` → Success with thumbnail/duration). 4. Resource links — repeatable `TextField` list, "Add Resource". 5. Save / Delete / Cancel.

**Primary CTA:** "Save Lesson". **Secondary:** "Delete Lesson", "Cancel".

**Components:** TextField, FileUpload, ProgressBar, PrimaryButton, TextButton.

**States:** Loading — skeleton form (editing existing). Error — inline field validation; upload error uses `FileUpload`'s own Error state (icon+message+retry). Success — `Snackbar` "Lesson saved" on save.

**Responsive:** single-column form, form-width cap.

**RTL/Accessibility:** same pattern as Course Editor — Overview (§ 21).

**Unsaved-changes guard:** navigating away with unsaved edits prompts a confirmation `AppDialog` (`NAVIGATION_SPEC.md § 6`).

---

### 24. Quiz Editor

**Platform:** Web · **Role:** Instructor

**Entry points:** Curriculum "Add Quiz" / edit existing quiz.
**Exit destinations:** Course Editor — Curriculum (Save/Cancel).

**Header structure:** minimal — "Course Quiz" title, Save/Cancel.

**Main content, in order:** 1. `ReorderableList` of questions, each: prompt `TextField`, 2–4 option `TextField`s, a "mark correct" selector. 2. "Add Question". 3. Save.

**Primary CTA:** "Save Quiz". **Secondary:** "Add Question", reorder/delete questions.

**Components:** ReorderableList/DragHandle, TextField, IconButton, TonalButton, PrimaryButton.

**States:** Loading — skeleton. Empty — "no questions yet," CTA "Add Question" (a lightweight in-context prompt, not a full `EmptyState` given the form context). Error — inline validation (e.g. "mark a correct answer before saving").

**Responsive:** single-column, form-width cap.

**RTL/Accessibility:** same reordering accessibility guarantee as Curriculum (§ 22) — Move Up/Down always present per question.

**Unsaved-changes guard:** same pattern as Lesson Editor.

---

## D. Admin (Web-only)

### 25. Admin Dashboard

**Platform:** Web · **Role:** Admin

**Entry points:** Login success (Admin role).
**Exit destinations:** Manage Courses, Manage Users, Manage Instructors, Manage Categories.

**Header structure:** Admin Sidebar + page title "Dashboard".

**Main content, in order:** 1. `StatCard` grid — total courses, total students, total instructors, published vs. draft split (4 cards, one row, `INSTRUCTOR_ADMIN_UX.md § 2`).

**Primary CTA:** none page-level (navigation is via Sidebar). **Secondary:** stat cards may be tappable shortcuts into their respective list screens (e.g. tapping "Total Courses" opens Manage Courses).

**Components:** StatCard.

**States:** Loading — skeleton stat cards. Error — `ErrorState` with retry.

**Responsive:** stat cards stack 2×2 or 1-per-row below Desktop.

**RTL/Accessibility:** stat card values are announced as text, not just large numerals with no label.

```
┌──────────────────────────────────────────┐
│ [Sidebar] Dashboard                       │
│ [ 42 courses ] [ 1.8k students ]          │
│ [ 15 instructors ] [ 30 published/12 draft]│
└──────────────────────────────────────────┘
```

---

### 26. Admin — Manage Courses

**Platform:** Web · **Role:** Admin

**Entry points:** Sidebar "Courses", Dashboard stat card, Manage Instructors (filtered).
**Exit destinations:** Course Details (read-only preview).

**Header structure:** page title "Courses" + `SearchField`.

**Main content, in order:** 1. Search/filter row. 2. `DataTable` (title, instructor, status `Badge`, enrollment count, row actions "View"/"Unpublish").

**Primary CTA:** none page-level. **Secondary:** search/filter, row actions.

**Components:** SearchField, Badge, DataTable, TonalButton.

**States:** Loading — skeleton table. Empty — "no results match your filters," `§ 2` (Admin lists row), CTA "Clear filters". Error — `ErrorState` with retry.

**Responsive:** `DataTable` collapses to cards below Desktop (`RESPONSIVE_BEHAVIOR.md § 6`).

**Unpublish confirmation:** a lightweight `AppDialog` confirms before unpublishing (a state-changing action, per `NAVIGATION_SPEC.md § 6`'s consistent confirmation pattern).

**RTL/Accessibility:** same `DataTable` semantics guarantee as Instructor Dashboard (§ 20).

```
┌──────────────────────────────────────────┐
│ [Sidebar] Courses     [ Search...    🔍 ] │
│ Title          Instructor   Status  Enr. │
│ Kotlin Basics   Jane Doe   Published 340 │
│ Advanced Kotlin Jane Doe   Draft       0 │
│ Intro to SQL    Sam Lee    Published 210 │
│                                [View][Unpublish]│
└──────────────────────────────────────────┘
```

---

### 27. Admin — Manage Users

**Platform:** Web · **Role:** Admin

**Entry points:** Sidebar "Users".
**Exit destinations:** none forward (view-only, terminal per `../product/USER_ROLES.md`).

**Header structure:** page title "Users" + `SearchField`.

**Main content, in order:** 1. Search row. 2. `DataTable` (name, email, join date, enrollment count) — view-only, no destructive row actions in MVP.

**Primary CTA:** none. **Secondary:** search.

**Components:** SearchField, Avatar, DataTable.

**States:** Loading — skeleton table. Empty — "no results," `§ 2`. Error — `ErrorState`.

**Responsive:** collapses to cards below Desktop.

**RTL/Accessibility:** same pattern as § 26.

---

### 28. Admin — Manage Instructors

**Platform:** Web · **Role:** Admin

**Entry points:** Sidebar "Instructors".
**Exit destinations:** Manage Courses (filtered by instructor).

**Header structure:** page title "Instructors" + `SearchField`.

**Main content, in order:** 1. Search row. 2. `DataTable` (name, email, course count, published count, row action "View Courses").

**Primary CTA:** none. **Secondary:** search, "View Courses" (navigates to § 26, pre-filtered).

**Components:** SearchField, Avatar, DataTable, TonalButton.

**States:** Loading — skeleton table. Empty — "no results," `§ 2`. Error — `ErrorState`.

**Responsive:** collapses to cards below Desktop.

**RTL/Accessibility:** same pattern as § 26.

---

### 29. Admin — Manage Categories

**Platform:** Web · **Role:** Admin

**Entry points:** Sidebar "Categories".
**Exit destinations:** none forward (terminal).

**Header structure:** page title "Categories" + "Add Category" `PrimaryButton`.

**Main content, in order:** 1. "Add Category" action. 2. Simple list (not a `DataTable` — few, flat rows, `INSTRUCTOR_ADMIN_UX.md § 2`): category name, course count, edit/remove actions per row.

**Primary CTA:** "Add Category". **Secondary:** edit/remove per row.

**Components:** TextField, PrimaryButton, TonalButton, CategoryChip (preview).

**States:** Loading — skeleton list. Empty — "no categories yet," CTA "Add Category". Error — `ErrorState` with retry.

**Guardrail:** removing a category with assigned courses is blocked or requires explicit confirmation with a clear explanation, via `AppDialog` — never a silent failure (`INSTRUCTOR_ADMIN_UX.md § 2`).

**Responsive:** single-column list at every breakpoint (no table-collapse concern — this was never a table).

**RTL/Accessibility:** category name and course count read together as one accessible row; the removal guardrail's explanation is announced, not just visually shown.
