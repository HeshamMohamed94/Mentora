# Mentora UX — Navigation Specification

The full navigation graph for all five audiences, built on [`../product/INFORMATION_ARCHITECTURE.md`](../product/INFORMATION_ARCHITECTURE.md)'s sitemaps and the design system's locked navigation components ([`../design-system/COMPONENTS.md § Navigation`](../design-system/COMPONENTS.md)). Screen names match [`SCREEN_UX_SPECS.md`](./SCREEN_UX_SPECS.md)/[`../product/SCREEN_INVENTORY.md`](../product/SCREEN_INVENTORY.md) exactly.

**Conventions:**
- **Parent** — the destination this screen is reached *from* in the primary navigation model (not every possible referrer, just the canonical one).
- **Children** — destinations reachable directly from this screen.
- **Entry points** — every UI trigger that can land a user here (may include more than the one "Parent" path, e.g. a course reachable from three different listings).
- **Back behavior** — what happens on browser-back (Web) or system-back/swipe-back (Mobile).
- **Deep-link-worthy** — whether this destination is the kind of thing worth a shareable/bookmarkable URL or a mobile deep link in a later technical phase. Flagged conceptually only — no linking is implemented in this planning phase.

---

## 1. Guest Web

| Screen | Parent | Children | Entry points | Back behavior | Deep-link-worthy |
|---|---|---|---|---|---|
| Landing | — (root) | Explore, Learning Paths, Login, Register, Course Details (via featured cards) | Direct URL, external links | N/A (root) | ✓ (the root URL) |
| Explore | Landing (Navbar) | Course Details | Navbar "Explore", Landing CTA | → Landing (or previous page if arrived via back-forward history) | ✓ (with query/filter state) |
| Course Details | Explore | Login/Register (enroll gate), Demo Checkout (if authenticated) | Explore grid, Learning Path Details, Landing featured cards, external share links (future) | → Explore (or previous listing) | ✓ — primary shareable unit |
| Learning Paths | Navbar | Learning Path Details | Navbar "Learning Paths" | → Landing/previous page | ✓ |
| Learning Path Details | Learning Paths | Course Details (per member course) | Learning Paths grid | → Learning Paths | ✓ |
| Login | Navbar / enroll-gate | Dashboard (post-auth), Register | Navbar "Login", any enroll-gate, Register's "Login" link | → the screen that triggered the gate (or Landing) | Not worth deep-linking (auth state dependent) |
| Register | Navbar / enroll-gate | Dashboard (post-auth), Login | Navbar "Get Started", any enroll-gate, Login's "Register" link | → the screen that triggered the gate (or Landing) | Not worth deep-linking |

**No dead ends:** every Guest screen has at least one forward path (into deeper discovery or into auth) and a defined back destination. Course Details is the hinge — its "Enroll"/"Buy" action for an unauthenticated Guest routes through Login/Register and back into the purchase flow automatically (`../product/USER_FLOWS.md § 8`), never stranding the user at a dead-end auth screen with no memory of what they were trying to do.

---

## 2. Student Web

Sidebar items (`Dashboard, Explore, My Learning, Learning Paths, AI Tutor, Certificates, Profile, Settings`) are always-available parents for their own subtree — "Parent" below names the *typical* path in, not the only one, since the Sidebar makes every top-level item reachable from anywhere.

| Screen | Parent | Children | Entry points | Back behavior | Deep-link-worthy |
|---|---|---|---|---|---|
| Dashboard | Sidebar (default landing post-login) | Course Player (Continue), Explore, My Learning, AI Tutor | Login success, Sidebar "Dashboard" | → previous page or no-op (it's the landing) | Not especially (personalized) |
| Explore | Sidebar | Course Details | Sidebar "Explore" | → Dashboard/previous page | ✓ (shared with Guest) |
| Course Details | Explore / My Learning / Learning Path Details | Demo Checkout, Course Player (if enrolled) | Explore grid, Dashboard recommendations, Learning Path Details, My Learning (if already enrolled, routes straight to Course Player instead) | → the screen that linked here | ✓ (shared with Guest) |
| Demo Checkout | Course Details | Purchase Success | Course Details "Enroll"/"Buy" | → Course Details (cancel) | Not worth deep-linking (transient) |
| Purchase Success | Demo Checkout | Course Player, My Learning | Automatic, on successful demo purchase | Back is disabled/redirected to My Learning (never re-enter a completed checkout — see § 6) | No |
| Course Player | Purchase Success / My Learning / Dashboard / Course Details | Quiz, AI Tutor (contextual) | "Start Learning," "Continue," Course Details "Continue Learning" | → My Learning (not Course Details, once enrolled — see rationale below) | ✓ (resume-at-lesson is a strong deep-link candidate for later) |
| Quiz | Course Player (automatic, last lesson) | Quiz Results | Automatic after last lesson; "Take Quiz" from My Learning/Course Player if revisiting | → Course Player (with answers preserved if mid-quiz, per `../product/USER_FLOWS.md § 14`) | No |
| Quiz Results | Quiz | Course Player (Retry) / My Learning (Continue, if passed) | Automatic after Submit | → Quiz (rare; typically forward-only) | No |
| My Learning | Sidebar | Course Player, Learning Path Details, Certificate Detail | Sidebar "My Learning" | → Dashboard/previous page | Not really (personalized list) |
| Learning Paths | Sidebar | Learning Path Details | Sidebar "Learning Paths" | → Dashboard/previous page | ✓ (shared with Guest) |
| Learning Path Details | Learning Paths / My Learning | Course Details (per member course) | Learning Paths grid, My Learning (followed paths) | → Learning Paths or My Learning depending on entry | ✓ |
| AI Tutor | Sidebar / Course Player (contextual) | — (self-contained) | Sidebar "AI Tutor", Course Player's AI Tutor affordance | → the screen it was opened from (Sidebar nav or Course Player) | Not in MVP (no shareable conversation) |
| Certificates | Sidebar | Certificate Detail | Sidebar "Certificates", My Learning (post-completion) | → Dashboard/previous page | Not really (personal record) |
| Certificate Detail | Certificates | — (terminal; Share is an action, not navigation) | Certificates grid, Quiz Results/Course Player completion confirmation | → Certificates | ✓ (the one certificate-specific case worth a future shareable link, distinct from the private list) |
| Profile | Sidebar / account menu | Settings | Sidebar "Profile" | → Dashboard/previous page | No |
| Settings | Profile | — (terminal) | Profile "Settings" | → Profile | No |

**Why Course Player backs to My Learning, not Course Details:** once enrolled, Course Details becomes a marketing/overview page a Student rarely needs again — routing "back" from the player to the full My Learning list (where they can resume *any* course) is more useful than routing to the single course's now-redundant details page. This is a deliberate, **approved** UX decision (Approved UX Decision 1, `../product/PRODUCT_SPEC.md` completion history) — not an open question.

---

## 3. Student Mobile (Android & iOS — same graph)

Root structure is the locked 5-item `MobileBottomNavigation`: **Home, Explore, My Learning, AI Tutor, Profile.** Each tab owns its own push/pop navigation stack (standard mobile tab-navigator behavior) — switching tabs does **not** push onto the previous tab's stack, and each tab remembers its own stack position when the user returns to it.

| Screen | Tab / Parent | Children | Entry points | Back behavior | Deep-link-worthy |
|---|---|---|---|---|---|
| Home | *(tab root)* | Course Player (Continue), Explore, My Learning, AI Tutor | App open (authenticated), tab tap | At tab root: Android hardware back exits the app (with a confirm-to-exit convention if desired, product decision, not required for MVP); iOS has no back gesture at a tab root | Not especially (personalized) |
| Explore | *(tab root)* | Course Details, Learning Paths (nested segment) | Tab tap | At tab root: same as Home | ✓ (shared concept with Web Explore) |
| Learning Paths | Explore (nested) | Learning Path Details | Explore's Learning Paths segment/tab | Pop to Explore | ✓ |
| Learning Path Details | Learning Paths | Course Details | Learning Paths list | Pop to Learning Paths | ✓ |
| Course Details | Explore (pushed) | Demo Checkout, Course Player | Explore grid, Learning Path Details, Home recommendations | Pop to whichever screen pushed it | ✓ |
| Demo Checkout | Course Details (pushed) | Purchase Success | Course Details "Enroll"/"Buy" | Pop to Course Details (cancel) | No |
| Purchase Success | Demo Checkout (pushed) | Course Player, My Learning | Automatic | Back is disabled/redirected to My Learning tab (§ 6) | No |
| Course Player | Explore or My Learning (pushed from either) | Quiz, AI Tutor (contextual), Curriculum Bottom Sheet (overlay, not a pushed screen) | "Start Learning," "Continue" from either Explore's Course Details or My Learning's `CourseProgressCard` | Pop to whichever tab/screen pushed it | ✓ (resume-at-lesson, future) |
| Quiz | Course Player (pushed) | Quiz Results | Automatic after last lesson; "Take Quiz" from My Learning/Course Player | Pop to Course Player, answers preserved | No |
| Quiz Results | Quiz (pushed) | Course Player (Retry) / My Learning (Continue) | Automatic after Submit | Pop to Quiz (rare) | No |
| My Learning | *(tab root)* | Course Player, Learning Path Details (via followed paths), Certificates (nested) | Tab tap | At tab root: same as Home | Not really |
| Certificates | My Learning (nested) | Certificate Detail | My Learning's Certificates entry | Pop to My Learning | Not really |
| Certificate Detail | Certificates | — (terminal) | Certificates list, post-completion confirmation | Pop to Certificates | ✓ (future) |
| AI Tutor | *(tab root)* / Course Player (contextual) | — | Tab tap, Course Player's AI Tutor affordance | At tab root: same as Home. From Course Player: pop back to Course Player | No |
| Profile | *(tab root)* | Settings | Tab tap | At tab root: same as Home | No |
| Settings | Profile (pushed) | — (terminal) | Profile "Settings" | Pop to Profile | No |
| Login | *(outside tab bar)* | Register, tabbed app (post-auth) | App open (unauthenticated), any enroll-gate | Platform-native modal/stack dismiss → previous pre-auth state or app exit if it's the first screen | No |
| Register | *(outside tab bar)* | Login, tabbed app (post-auth) | Login's "Register" link, any enroll-gate | Same as Login | No |

**Guest browsing on mobile:** Explore, Course Details, and Learning Paths/Details are reachable **before** login (`../product/USER_ROLES.md`) — the bottom nav itself is only shown once authenticated (pre-auth, Explore is reached via a lighter entry surfaced from the Login/Register stack, e.g. a "Browse as guest" affordance, or the app opens straight into a Guest-mode Explore with Login/Register presented as needed at the enroll-gate). This mirrors Web's Guest IA (§ 1) rather than inventing a mobile-only pattern.

---

## 4. Instructor Web

| Screen | Parent | Children | Entry points | Back behavior | Deep-link-worthy |
|---|---|---|---|---|---|
| Instructor Dashboard | Sidebar (default landing) | Course Editor — Overview (per course), Course Editor — Overview (new, via Create Course) | Login success (Instructor role) | → previous page / no-op | No (internal tool) |
| Course Editor — Overview | Instructor Dashboard | Course Editor — Curriculum | Dashboard "Create Course" / open an existing course | → Instructor Dashboard | No |
| Course Editor — Curriculum | Course Editor — Overview (tab sibling) | Lesson Editor, Quiz Editor | Overview tab's "Curriculum" tab | → Course Editor — Overview (or stays, since it's a tab, not a push) | No |
| Lesson Editor | Course Editor — Curriculum | — (terminal; Save/Cancel return) | Curriculum "Add Lesson" / edit existing lesson | → Course Editor — Curriculum | No |
| Quiz Editor | Course Editor — Curriculum | — (terminal) | Curriculum "Add Quiz" / edit existing quiz | → Course Editor — Curriculum | No |

Overview and Curriculum are **tabs within one Course Editor screen-group** (`../product/INFORMATION_ARCHITECTURE.md § 4`), not a push/pop pair — switching tabs preserves unsaved-state warnings consistently (see § 7 below).

---

## 5. Admin Web

| Screen | Parent | Children | Entry points | Back behavior | Deep-link-worthy |
|---|---|---|---|---|---|
| Admin Dashboard | Sidebar (default landing) | Manage Courses, Manage Users, Manage Instructors, Manage Categories | Login success (Admin role) | → previous page / no-op | No |
| Manage Courses | Sidebar / Admin Dashboard | Course Details (read-only preview) | Sidebar "Courses", Dashboard stat cards, Manage Instructors (filtered view) | → Admin Dashboard | No |
| Manage Users | Sidebar / Admin Dashboard | — (view-only, terminal) | Sidebar "Users" | → Admin Dashboard | No |
| Manage Instructors | Sidebar / Admin Dashboard | Manage Courses (filtered by instructor) | Sidebar "Instructors" | → Admin Dashboard | No |
| Manage Categories | Sidebar / Admin Dashboard | — (terminal) | Sidebar "Categories" | → Admin Dashboard | No |

---

## 6. Cross-Cutting Navigation Rules

- **No screen in any graph above is a dead end.** Every terminal node (Settings, Lesson Editor, Certificate Detail, Manage Users, etc.) has a defined "Back behavior" that returns the user somewhere coherent; every non-terminal node has at least one child.
- **Post-success screens don't allow "back into" the action that produced them.** Purchase Success explicitly disables/redirects browser-back and system-back to My Learning rather than letting the user "go back" into a completed Demo Checkout and risk re-triggering it — the same pattern any real checkout flow uses to avoid duplicate submissions, even though Mentora's checkout is simulated.
- **Quiz answers survive back-navigation.** Backing out of Quiz mid-attempt (Web or Mobile) preserves selected answers rather than discarding progress, per `../product/USER_FLOWS.md § 14`.
- **Unsaved-content warning (Instructor).** Navigating away from Lesson Editor, Quiz Editor, or Course Editor — Curriculum with unsaved changes prompts a lightweight `AppDialog` confirmation ("Discard unsaved changes?") — this applies uniformly across all three, not ad hoc per screen.
- **AI Tutor never traps navigation.** Opened globally (Sidebar/bottom-nav tab) or contextually (from Course Player), it always has a clear way back to wherever it was opened from — it's a utility surface, never a flow the user gets "stuck" inside.
- **Enroll-gate always returns to intent.** Any unauthenticated attempt to enroll/follow-a-path routes through Login/Register and lands back in the original flow (Demo Checkout, Follow Path) automatically — never dropping the user at a generic post-login landing screen when they had a specific intent (`../product/USER_FLOWS.md §§ 1, 2, 8, 21`).
