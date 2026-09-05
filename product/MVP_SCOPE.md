# Mentora — MVP Scope

Governing principle (from [`PRODUCT_SPEC.md`](./PRODUCT_SPEC.md)): Mentora is not a Udemy/Coursera clone. The MVP is the smallest set of features that produces a complete, credible, end-to-end learning-platform product — sized for a solo/small-team portfolio build, not an enterprise roadmap.

---

## 1. MVP

Organized by product area. Every row here is expected to map to at least one entry in [`USER_FLOWS.md`](./USER_FLOWS.md) and [`SCREEN_INVENTORY.md`](./SCREEN_INVENTORY.md) (cross-checked in § 4 of this file).

### Authentication & Account
- Registration (email + password)
- Login
- Logout
- Student profile (view/edit name, avatar, basic info)

*(Forgot/reset password was considered as a pragmatic addition and has been moved to Post-MVP per approved product decision — see § 2. No password-recovery infrastructure is built for the first portfolio demo.)*

### Course Discovery
- Browse courses (catalog/grid)
- Search courses (keyword)
- Filter courses (category, level, price — basic filter set, not faceted search)
- Course details page

### Demo Purchase & Enrollment
- Demo course purchase / simulated checkout (see [`DEMO_PAYMENT_FLOW.md`](./DEMO_PAYMENT_FLOW.md) — no real payment)
- Enrollment (created on successful demo checkout)

### Learning Experience
- Course player (sections, lessons, video)
- Lessons (title, description, video, resources, completion state)
- Course progress tracking
- Resume learning (continue where left off)
- Quiz (multiple-choice, per course)
- Quiz results (score, correct/incorrect breakdown — icon + text + color, never color-only)
- Course completion
- Certificate (view; UI-only share affordance)

### Student Home
- Student dashboard (Web) / Home (Mobile)
- My Learning (enrolled courses + progress)
- Learning Paths (browse + follow a curated path)

### AI Tutor
- Basic AI Tutor: lesson-context-aware chat + the 5 fixed quick actions (see `PRODUCT_SPEC.md § 11`)

### Localization *(v1.3 — locked MVP requirement)*
- Functional English + Arabic UI localization across Public Website, Student Website, Android, iOS, Instructor Web, and Admin Web — not Post-MVP, not "Coming Soon" (see `PRODUCT_SPEC.md § 16`).
- Functional language selector (Dropdown/Select) inside Settings/Profile; switches UI strings, text direction, and layout direction with no new account required.
- Full RTL layout support for Arabic, with the VideoPlayer scrubber/timeline and the Mentora wordmark as the only fixed-LTR exceptions.
- Locale-aware date/number/duration/price formatting.
- Course-content language remains a separate, out-of-scope-for-translation concept — see § 2.

### Instructor (Web-first)
- Instructor dashboard (their courses list)
- Instructor creates a course (metadata: title, description, category, price, thumbnail)
- Instructor manages sections and lessons (add/edit/reorder)
- Instructor adds a quiz to a course
- Basic course publishing state (Draft ⇄ Published)

### Admin (Web-first, basic)
- Admin dashboard (basic platform overview: counts of courses/users/instructors)
- Manage courses (list, view, unpublish if needed)
- Manage categories (CRUD)
- Manage users/instructors (list, basic role visibility)

---

## 2. Post-MVP

Real, planned functionality — deliberately not in the first build, but consistent with where Mentora is headed:

| Feature | Why deferred |
|---|---|
| Forgot / reset password (email recovery) | Approved product decision: no password-recovery infrastructure for the first portfolio demo. Login/Registration do not depend on it — nothing else in MVP is blocked by this deferral. |
| Real payment integration (any gateway) | Explicit MVP constraint — see `DEMO_PAYMENT_FLOW.md`; real money handling adds compliance/security scope disproportionate to a portfolio MVP. |
| Course reviews & ratings (student-authored, moderated) | A *static/seed* rating value may be displayed for catalog realism (see `PRODUCT_SPEC.md § 9`); the full write/moderate/aggregate flow is a separate feature. |
| Notifications (push/email/in-app) | Needs its own infrastructure and settings surface; not required to demonstrate the core learning journey. |
| Discussion / Q&A per lesson | A community feature, not core to "one student learning a course." |
| Multi-instructor marketplace mechanics (revenue share, payouts, instructor analytics beyond basics) | Mentora MVP is single-catalog, not a competitive marketplace. |
| Automatic translation of course *content* (video, instructor-written lesson text, resources, subtitles) | UI localization (English + Arabic) is now a locked MVP requirement — see § 1 Localization and `PRODUCT_SPEC.md § 16`. Course content stays in whatever language the instructor authored it in; translating that content is a content-production effort, not a UI one, and is explicitly not automated even Post-MVP unless separately provided. |
| Assignments / peer-graded work | Only auto-graded quizzes in MVP. |
| Course bundles / subscriptions | MVP sells individual courses only. |
| Coupons / discounts / promotions | No pricing-logic surface needed for a demo checkout. |
| Wishlist / "save for later" | Nice, not necessary for the core journey. |
| Real LinkedIn "Add to Profile" API integration | MVP certificate share is a UI affordance only. |
| SSO (Google/Apple sign-in) | Email/password is sufficient to demonstrate auth; SSO is additive. |
| Mobile offline downloads | Meaningful engineering scope for a feature that doesn't change the core demo narrative. |
| Instructor/Admin native mobile apps | Explicit product decision: Instructor/Admin stays Web-first (see `USER_ROLES.md`). |
| Advanced admin (audit log, granular permissions, bulk ops) | Enterprise-scale functionality the brief explicitly says to avoid. |

## 3. Nice-to-Have / Future Ideas

Lower-confidence, exploratory — not committed even for Post-MVP:

- Gamification (streaks, XP, badges beyond the certificate itself)
- Adaptive/algorithmic Learning Path recommendations
- AI-generated quiz questions (vs. instructor-authored)
- Voice interaction with AI Tutor
- Live cohort sessions / webinars
- Referral program
- Public, verifiable certificate URLs
- Dedicated instructor analytics dashboard (engagement heatmaps, drop-off points)
- In-app messaging between student and instructor
- A/B testing infrastructure for course pages

---

## 4. MVP Realism Check

Cross-checked against `PRODUCT_SPEC.md` and the flow/screen files:

- Every MVP bullet in § 1 maps to at least one flow in `USER_FLOWS.md` and at least one screen in `SCREEN_INVENTORY.md` — verified in this planning pass (see the Final Review note in `SCREEN_INVENTORY.md`).
- Screen count stays at 29 (`SCREEN_INVENTORY.md`), consistent with "focused, not Udemy-sized."
- No enterprise-only capability (bulk admin ops, audit logging, granular permission editor, analytics suite) made it into MVP.
- No real financial system, real AI provider integration, or real third-party auth appears in MVP — all three are explicitly deferred/out of scope per `PRODUCT_SPEC.md`.

## 5. Approved Product Decisions

Reviewed and closed — recorded here for traceability:

| Decision | Resolution |
|---|---|
| Forgot / Reset Password | **Moved to Post-MVP** (§ 2). No password-recovery infrastructure built for the first portfolio demo; Login/Registration have no dependency on it. |
| Account roles | **Exactly one role per account in MVP.** Student, Instructor, and Admin use separate accounts — see `USER_ROLES.md § Decisions`. |
| Admin course review | **No formal submit/approval/rejection workflow in MVP.** Admin views/manages courses and unpublishes when necessary; a moderation queue is Post-MVP — see `USER_ROLES.md § Decisions`. |
| Public Navbar | **"Pricing" removed** from public navigation — Mentora uses individually priced demo courses, not a subscription tier — see `INFORMATION_ARCHITECTURE.md § 1`. |
