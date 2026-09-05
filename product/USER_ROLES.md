# Mentora — User Roles

Four roles for MVP. **One role per account in MVP (approved)** — a user is a Guest (unauthenticated), Student, Instructor, or Admin; there is no dual-role account (e.g., an Instructor who is also enrolled as a Student on the same account) in MVP; Student, Instructor, and Admin use separate accounts. This keeps auth/permissioning simple and is a confirmed product decision (see § Decisions).

---

## Guest

Unauthenticated visitor, on Web or Mobile.

**Capabilities:**
- Browse public marketing pages (Landing)
- Browse courses (Explore/catalog)
- Search courses
- Filter courses
- View course details (full — including curriculum outline, instructor info, price)
- View public Learning Paths (browse only)
- Register
- Login

**Cannot:**
- Access any enrolled-course content (lesson video, resources, quiz)
- Purchase/enroll (must register/login first — see `USER_FLOWS.md § Demo Purchase`)
- Access My Learning, AI Tutor, Certificates, Profile, Settings, or any Instructor/Admin surface
- Persist any state across sessions (no saved filters, no cart — MVP has no cart concept; see `DEMO_PAYMENT_FLOW.md`), **except UI language** — a Guest can switch English/Arabic and that choice persists locally on-device (no account, no server sync; see `PRODUCT_SPEC.md § 16`)

**Platform note:** Guest browsing is available on Web **and** Mobile (a visitor can open the mobile app and browse before creating an account) — Mentora does not force a login wall before discovery on any platform.

---

## Student

Authenticated learner. The primary MVP role and the one with the richest cross-platform surface.

**Capabilities:**
- Manage profile (name, avatar, basic info)
- Everything a Guest can do, plus:
- Demo-purchase courses (simulated checkout, no real payment)
- Enroll (result of a successful demo purchase)
- Access purchased/enrolled course content
- Watch lessons (video), view lesson resources
- Track progress (per-lesson and per-course)
- Resume learning (continue from last position)
- Mark/complete lessons
- Take quizzes
- See quiz results (score + correct/incorrect breakdown)
- Complete courses (once all lessons + quiz, if present, are done)
- Receive and view certificates
- Use AI Tutor (global and lesson-context)
- Follow Learning Paths and track path-level progress
- View My Learning (all enrollments + progress in one place)
- Set UI language (English/Arabic) via Settings; preference persists to the account for cross-device consistency, in addition to the local on-device copy (`PRODUCT_SPEC.md § 16`)

**Cannot:**
- Access any Instructor or Admin surface
- Access another student's data (progress, certificates, profile)
- Edit course content

**Platform note:** the full Student capability set is available identically on **Web, Android, and iOS** — this is the one role that is genuinely "the same product on three platforms," per `PRODUCT_SPEC.md § 10`.

---

## Instructor

Authenticated course creator. **Web-only in MVP** — not a limitation of mobile technology, a deliberate product decision: course authoring is a low-frequency, desk-based, content-heavy workflow (uploading video, writing structured curriculum) that fits a Web-first tool, and building/maintaining a second full authoring UI on mobile is scope Mentora's MVP does not need to justify.

**Capabilities:**
- Instructor dashboard (their own courses, basic per-course stats: enrollment count, completion rate)
- Create course (title, description, category, price, thumbnail)
- Edit course (metadata)
- Add sections
- Add lessons (video, title, description, resources)
- Reorder sections and lessons
- Add quiz content (questions, options, correct answer) to their course
- Publish / unpublish their course (Draft ⇄ Published)
- View basic course/student statistics for their own courses (enrollment count, completion rate — no deep analytics)
- Set UI language (English/Arabic) via Settings, persisted to the account (`PRODUCT_SPEC.md § 16`) — separate from any course's content language

**Cannot:**
- Edit or manage another instructor's course
- Manage users, categories, or platform-wide settings (Admin-only)
- Access the Student learning surfaces as a learner under the same account (see § Decisions — a person who wants both roles uses two accounts in MVP)
- Set real prices tied to a real payment processor (prices are demo/display values — see `DEMO_PAYMENT_FLOW.md`)

**Statistics scope (kept simple per the brief):** enrollment count and completion rate per course only — no engagement heatmaps, drop-off funnels, or revenue analytics (revenue is not real; see `MVP_SCOPE.md § Post-MVP`).

---

## Admin

Authenticated platform operator. **Web-only in MVP**, same rationale as Instructor — an operational tool, not a learner-facing surface.

**Capabilities:**
- Admin dashboard (basic platform overview: total courses, total users, total instructors, published vs. draft counts)
- Manage courses (list all courses across all instructors; view; unpublish if needed — e.g., inappropriate content)
- Manage users (list students; view basic account info)
- Manage instructors (list instructors; view their courses)
- Manage categories (create/edit/remove course categories)
- Review published content (a course review is effectively "view the course as it would appear to a student" plus the ability to unpublish — no separate formal review/approval workflow in MVP; see § Decisions)
- Set UI language (English/Arabic) via Settings, persisted to the account (`PRODUCT_SPEC.md § 16`)

**Cannot:**
- Edit another user's course content directly (can unpublish, not rewrite — preserves instructor ownership of their content)
- Access real financial/payment data (none exists — see `DEMO_PAYMENT_FLOW.md`)
- Perform bulk operations, view audit logs, or edit granular permissions (explicitly avoided per the brief — "avoid overbuilding enterprise administration")

**Platform note:** Admin is Web-only for the same reason as Instructor — this is a low-frequency, desk-based operational role.

---

## Role Summary Table

| Capability area | Guest | Student | Instructor | Admin |
|---|:---:|:---:|:---:|:---:|
| Browse/search/filter courses | ✓ | ✓ | ✓ (as browsing, not authoring context) | ✓ |
| View course details | ✓ | ✓ | ✓ | ✓ |
| Register / Login | ✓ | — (already authenticated) | — | — |
| Demo-purchase & enroll | — | ✓ | — | — |
| Learn (player, progress, quiz, certificate) | — | ✓ | — | — |
| AI Tutor | — | ✓ | — | — |
| Learning Paths | Browse only | Follow + track | — | — |
| Create/edit own course content | — | — | ✓ | — |
| Publish/unpublish own course | — | — | ✓ | ✓ (any course, moderation only) |
| Manage users/instructors/categories | — | — | — | ✓ |
| Platform overview | — | — | Own courses only | Platform-wide |

---

## Decisions (approved)

1. **One role per account in MVP — approved.** No account holds both Student and Instructor capabilities; Student, Instructor, and Admin each use separate accounts. A dual-role account (e.g., an instructor who also takes courses on the same login) is not required in MVP and is Post-MVP if ever needed.
2. **No formal Admin content-review workflow — approved.** "Review published content" in MVP means an Admin can browse any course as it will appear to students and unpublish it if needed — there is no submit-for-review/approval-queue state machine. Instructors publish directly; Admin moderates after the fact. A formal moderation queue is Post-MVP.
3. **Instructor/Admin are Web-only by product decision**, not a stated technical constraint — explicitly confirmed against the brief's "Instructor functionality should primarily live on Web" and "Admin functionality should primarily live on Web."
