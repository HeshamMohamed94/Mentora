# Mentora — Information Architecture

Four IAs, one per surface, all drawing from the same [`USER_ROLES.md`](./USER_ROLES.md) and the design system's locked navigation components ([`../design-system/COMPONENTS.md § Navigation`](../design-system/COMPONENTS.md)). Screen-level detail lives in [`SCREEN_INVENTORY.md`](./SCREEN_INVENTORY.md) — this file defines *structure and navigation*, not screen content.

---

## 1. Public Website (Guest)

Uses the design system's `Navbar` (logo, links, search, auth actions).

```
Landing (/)
├─ Explore Courses (/explore)
│   └─ Course Details (/courses/:slug)
├─ Learning Paths (/paths)
│   └─ Learning Path Details (/paths/:slug)
├─ Login (/login)
└─ Register (/register)
```

**Approved decision — no "Pricing" in the public nav:** the design system's original Navbar spec included an example "Pricing" link. Mentora sells individual courses at individual prices (§ `PRODUCT_SPEC.md`), not a subscription tier — there is no separate pricing *page* to link to; each course's demo price lives on its own Course Details page. **Confirmed:** the public Navbar's actual link set is **Explore, Learning Paths, Search, Login, Get Started** — this is a content decision, not a change to the `Navbar` component's structure (logo / links / search / auth actions, per the design system, is unchanged).

**Note on Forgot Password:** moved to Post-MVP (see `MVP_SCOPE.md § 2`) — Login has no password-recovery branch in this MVP IA. Re-add `Login → Forgot Password` here if/when that feature is scheduled.

**Course Details** is reachable from Explore and Learning Path Details, and is the hinge point into authentication: its primary action ("Enroll"/"Buy") routes an unauthenticated Guest to Login/Register first, then continues into Demo Checkout (see `USER_FLOWS.md`).

---

## 2. Authenticated Student Website

Uses the design system's `Sidebar` (fixed item set, per the locked design system): **Dashboard, Explore, My Learning, Learning Paths, AI Tutor, Certificates, Profile, Settings.**

```
Dashboard (/app)
Explore (/app/explore)                      [same catalog as public Explore, enrollment-aware]
  └─ Course Details (/app/courses/:slug)
       └─ Demo Checkout (/app/checkout/:courseId)   [if not enrolled]
            └─ Purchase Success (/app/checkout/:courseId/success)
       └─ Course Player (/app/learn/:courseId)        [if enrolled]
            ├─ Lesson view (within player, not a separate route concept)
            ├─ Quiz (/app/learn/:courseId/quiz)
            └─ Quiz Results (/app/learn/:courseId/quiz/results)
My Learning (/app/my-learning)
  └─ (links into Course Player for any enrolled course)
Learning Paths (/app/paths)
  └─ Learning Path Details (/app/paths/:slug)
       └─ (links into that path's member Course Details)
AI Tutor (/app/ai-tutor)                      [also reachable contextually from within Course Player]
Certificates (/app/certificates)
  └─ Certificate Detail (/app/certificates/:id)
Profile (/app/profile)
Settings (/app/settings)
```

**Notes:**
- Course Details, Explore, and Learning Paths are the *same screens* a Guest sees, rendered with student-aware state (enrolled/not-enrolled, progress shown) — not duplicate screens. This is why `SCREEN_INVENTORY.md` marks them **Both** roles rather than listing separate Guest/Student versions.
- The Course Player is the one screen with the deepest internal structure (section/lesson navigation, video, resources) — it is treated as a single screen with rich internal state in this IA, detailed in `SCREEN_INVENTORY.md`, not decomposed into one route per lesson.
- AI Tutor has two entry points (Sidebar item, and a contextual affordance inside Course Player) but is one screen/surface, carrying lesson context when entered the second way (`PRODUCT_SPEC.md § 11`).
- **Language selector (v1.3, locked MVP):** lives inside Settings (`/app/settings`), not the Sidebar or Navbar directly — a functional English/العربية Select, not a new screen (`PRODUCT_SPEC.md § 16`).

---

## 3. Mobile App (Android & iOS — same IA)

Primary navigation is the design system's locked `MobileBottomNavigation`: **Home, Explore, My Learning, AI Tutor, Profile** (5 items, fixed). This is a hard constraint from the locked design system — mobile IA is *not* a shrunk version of the Web sidebar, and desktop navigation behavior is not forced onto it.

```
[Bottom Nav] Home
  └─ (surfaces continue-learning + recommended, links into My Learning / Course Player)

[Bottom Nav] Explore
  └─ Course Details
       └─ Demo Checkout → Purchase Success
       └─ Course Player → Quiz → Quiz Results
  └─ Learning Paths                         [lives under Explore on mobile — see placement note]
       └─ Learning Path Details

[Bottom Nav] My Learning
  └─ (links into Course Player for any enrolled course)
  └─ Certificates                            [lives under My Learning on mobile — see placement note]
       └─ Certificate Detail

[Bottom Nav] AI Tutor
  (also reachable contextually from within Course Player, same as Web)

[Bottom Nav] Profile
  └─ Settings
  └─ (Login/Register live outside the bottom nav — see below)
```

**Where supporting screens live (since only 5 top-level destinations exist):**

| Screen | Lives under | Rationale |
|---|---|---|
| Course Details | Explore (pushed) | Discovery is Explore's job; details is one tap deeper. |
| Demo Checkout / Purchase Success | Course Details (pushed) | Purchase is a continuation of viewing a specific course, not its own destination. |
| Course Player, Quiz, Quiz Results | Explore *or* My Learning (pushed from either) | A student can start a course from Explore (new) or resume it from My Learning (in progress) — same screen, two entry points. |
| Learning Paths + Detail | Explore (pushed, e.g. a top segment/tab within Explore) | Learning Paths are a discovery concept, same as courses — kept under Explore rather than earning a 6th bottom-nav slot, which the locked design system doesn't allow. |
| Certificates + Detail | My Learning (pushed) | A certificate is the end-state of an enrollment tracked in My Learning. |
| Settings | Profile (pushed) | Standard mobile convention; Profile is the account-surface root. |
| Login / Register | Presented outside the tab bar (modal/full-screen stack) pre-authentication; Logout returns here | These aren't tabbed destinations — they gate entry into the tabbed app, consistent with Guest browsing being allowed pre-login (§ `USER_ROLES.md`) while purchase/learning still requires auth. Forgot Password is Post-MVP (`MVP_SCOPE.md § 2`) and has no slot here yet. |

**Explicitly not done:** no attempt to replicate the Web Sidebar's 8-item flat list on mobile. Learning Paths and Certificates deliberately nest under an existing tab rather than requesting a 6th tab — the locked design system caps `MobileBottomNavigation` at 5 items (`COMPONENTS.md`), and product planning respects that rather than proposing a change to it.

**Language selector (v1.3, locked MVP):** lives inside Profile → Settings, same placement logic as Web — not a 6th bottom-nav tab (`PRODUCT_SPEC.md § 16`).

---

## 4. Instructor/Admin Website

Web-only (`USER_ROLES.md`). Uses the same `Sidebar` component as the Student website, but with a role-specific item set — the component contract is shared; the content is not.

```
Instructor Sidebar:
  Dashboard (/instructor)
  My Courses (/instructor/courses)
    └─ Course Editor — Overview (/instructor/courses/:id)
         └─ Course Editor — Curriculum (/instructor/courses/:id/curriculum)
              └─ Lesson Editor (/instructor/courses/:id/lessons/:lessonId)
              └─ Quiz Editor (/instructor/courses/:id/quiz)
  Profile / Account (/instructor/profile)

Admin Sidebar:
  Dashboard (/admin)
  Courses (/admin/courses)
  Users (/admin/users)
  Instructors (/admin/instructors)
  Categories (/admin/categories)
  Profile / Account (/admin/profile)
```

**Notes:**
- Instructor and Admin are presented as **separate sidebars/apps-within-the-app**, not a merged nav with conditional items — an Instructor account never sees Admin items and vice versa (single-role accounts, § `USER_ROLES.md`).
- Course Editor is one screen-group with two tabs/sub-views (**Overview**: metadata + publish control; **Curriculum**: sections/lessons/reorder) rather than a multi-page wizard — kept simple per `PRODUCT_SPEC.md § 14`'s course model.
- Admin's "Courses" list is where moderation (unpublish) happens — there is no separate "review queue" screen (`USER_ROLES.md § Assumptions`).
- **Language selector (v1.3, locked MVP):** lives inside each role's "Profile / Account" screen — Instructor and Admin get the same functional English/العربية Select as Student Settings, without a dedicated Settings screen of their own (`PRODUCT_SPEC.md § 16`).
