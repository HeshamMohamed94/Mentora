# Mentora — Screen Inventory (MVP)

**29 unique MVP screens.** "Platform: Both" means one screen concept shared by Web and Mobile (per [`INFORMATION_ARCHITECTURE.md`](./INFORMATION_ARCHITECTURE.md)), not a duplicate build — content/state is identical, layout adapts per the design system's responsive rules. Design system components referenced are named exactly as in [`../design-system/COMPONENTS.md`](../design-system/COMPONENTS.md), which is now **v1.2** — the seven components this inventory originally flagged as proposed (VideoPlayer/PlaybackControls, Checkout/OrderSummary, SuccessState, Toggle/Switch, FileUpload, ReorderableList/DragHandle, DataTable) were added there and are referenced below as real, specified components, each marked *(added DS v1.2)* on first use per screen.

---

## A. Public / Pre-Auth

### 1. Landing
- **Platform:** Web only (see `INFORMATION_ARCHITECTURE.md § 3` — mobile has no marketing landing page)
- **User role:** Guest
- **Purpose:** First impression; convert a visitor into a browser or a registrant.
- **Main content:** Hero (value prop + tagline "Learn. Build. Grow."), featured/popular courses, featured Learning Paths, brief "why Mentora" section.
- **Primary actions:** "Explore Courses", "Get Started" (Register)
- **Secondary actions:** "Login", browse a featured `CourseCard`/`LearningPathCard` directly
- **Design system components:** Navbar, PrimaryButton, TonalButton, CourseCard, LearningPathCard, StatCard (optional — e.g. "10 courses, 500 students" style trust markers)
- **MVP / Post-MVP:** MVP
- **Related flows:** none directly (entry point into Flows 1, 2, 4)

### 2. Explore
- **Platform:** Both
- **User role:** Guest, Student
- **Purpose:** Primary course discovery surface — browse, search, filter.
- **Main content:** `SearchField`, filter chips (category/level/price), responsive `CourseCard` grid (per `DESIGN_SYSTEM.md` course-grid columns).
- **Primary actions:** Tap a course → Course Details; enter a search query
- **Secondary actions:** Apply/clear filters; (Student only) toggle to Learning Paths sub-section per mobile IA
- **Design system components:** SearchField, CategoryChip, CourseCard, EmptyState (no results), LoadingState (skeleton grid), ErrorState
- **MVP / Post-MVP:** MVP
- **Related flows:** 4 (Browse), 5 (Search), 6 (Filter)

### 3. Course Details
- **Platform:** Both
- **User role:** Guest, Student
- **Purpose:** Full information needed to decide to enroll; entry point to purchase or continue learning.
- **Main content:** Thumbnail/preview, title, instructor (`InstructorCard` summary), price, curriculum outline (sections/lesson titles), rating (seed/static), student count, description.
- **Primary actions:** "Enroll"/"Buy Course" (not enrolled) or "Continue Learning" (enrolled)
- **Secondary actions:** View instructor profile summary; share course (UI-only, optional)
- **Design system components:** CourseCard (hero variant), InstructorCard, CategoryChip, Badge, PrimaryButton, TonalButton, ProgressBar (if enrolled, in-progress)
- **MVP / Post-MVP:** MVP
- **Related flows:** 7, 8, 9, 10

### 4. Learning Paths (Explore)
- **Platform:** Both
- **User role:** Guest, Student
- **Purpose:** Browse curated multi-course tracks.
- **Main content:** Grid/list of `LearningPathCard`s (title, course count, description).
- **Primary actions:** Tap a path → Learning Path Details
- **Secondary actions:** none
- **Design system components:** LearningPathCard, EmptyState, LoadingState
- **MVP / Post-MVP:** MVP
- **Related flows:** 21

### 5. Learning Path Details
- **Platform:** Both
- **User role:** Guest, Student
- **Purpose:** Show a path's ordered courses and let a student commit to following it.
- **Main content:** Path title/description, ordered `CourseCard` list (numbered sequence), path-level `ProgressBar` (if following).
- **Primary actions:** "Follow Path" (Student) / prompt to Login (Guest); tap a member course → Course Details
- **Secondary actions:** none
- **Design system components:** LearningPathCard (header variant), CourseCard, ProgressBar, PrimaryButton
- **MVP / Post-MVP:** MVP
- **Related flows:** 21

### 6. Login
- **Platform:** Both
- **User role:** Guest
- **Purpose:** Authenticate an existing user.
- **Main content:** Email + password `TextField`/`PasswordField`.
- **Primary actions:** "Login"
- **Secondary actions:** "Register" link *(no "Forgot password?" in MVP — deferred to Post-MVP, see `MVP_SCOPE.md § 2`)*
- **Design system components:** TextField, PasswordField, PrimaryButton, TextButton
- **MVP / Post-MVP:** MVP
- **Related flows:** 2

### 7. Register
- **Platform:** Both
- **User role:** Guest
- **Purpose:** Create a new Student account.
- **Main content:** Name, email, password `TextField`/`PasswordField`.
- **Primary actions:** "Create Account"
- **Secondary actions:** "Login" link (already have an account)
- **Design system components:** TextField, PasswordField, PrimaryButton, TextButton
- **MVP / Post-MVP:** MVP
- **Related flows:** 1

*(Forgot Password screen removed from MVP inventory — moved to Post-MVP per approved product decision. See `MVP_SCOPE.md § 2, § 5`. When scheduled, it reuses `TextField`/`PrimaryButton`/an `EmptyState`-style confirmation message — no new component required.)*

---

## B. Student (Authenticated)

### 8. Dashboard / Home
- **Platform:** Both (Web: "Dashboard"; Mobile: "Home" — same purpose, per `INFORMATION_ARCHITECTURE.md`)
- **User role:** Student
- **Purpose:** Personalized landing after login; fastest path back into active learning.
- **Main content:** "Continue learning" module (`CourseProgressCard` for the most recent in-progress course), recommended/next courses, Learning Paths in progress, quick stats (`StatCard`: courses in progress, completed, certificates).
- **Primary actions:** "Continue" on the resume module → Course Player
- **Secondary actions:** Jump to Explore; jump to My Learning; jump to AI Tutor
- **Design system components:** CourseProgressCard, StatCard, LearningPathCard, CourseCard
- **MVP / Post-MVP:** MVP
- **Related flows:** 12, 13

### 9. My Learning
- **Platform:** Both
- **User role:** Student
- **Purpose:** Single place to see every enrollment and its progress.
- **Main content:** List/grid of `CourseProgressCard`s (in-progress and completed, sectioned or filterable), followed Learning Paths with path-level progress.
- **Primary actions:** "Continue"/"Review" on a course
- **Secondary actions:** Filter by status (in progress / completed); jump to a followed Learning Path
- **Design system components:** CourseProgressCard, LearningPathCard, ProgressBar, EmptyState (no enrollments yet — CTA to Explore)
- **MVP / Post-MVP:** MVP
- **Related flows:** 12, 13, 17

### 10. Course Player
- **Platform:** Both
- **User role:** Student
- **Purpose:** Core learning surface — watch lessons, track progress, navigate curriculum.
- **Main content:** Video player, current lesson title/description/resources, section/lesson navigation list with per-lesson completion state, overall course `ProgressBar`.
- **Primary actions:** Play/pause video; "Mark Complete" / auto-advance to next lesson
- **Secondary actions:** Jump to any unlocked lesson; open AI Tutor (contextual); open Resources
- **Design system components:** VideoPlayer/PlaybackControls (added DS v1.2), ProgressBar, Badge (lesson complete indicator), Tabs (optional — Lessons/Resources), AITutorQuickAction entry point
- **MVP / Post-MVP:** MVP
- **Related flows:** 10, 11, 12, 13, 19

### 11. Quiz
- **Platform:** Both
- **User role:** Student
- **Purpose:** Assess understanding at course end.
- **Main content:** `QuizCard` question, multiple-choice options, question-progress indicator ("Question 3 of 10").
- **Primary actions:** Select an answer; "Next"; "Submit Quiz" (final question)
- **Secondary actions:** none (no skip in MVP — all questions required)
- **Design system components:** QuizCard, ProgressBar, PrimaryButton
- **MVP / Post-MVP:** MVP
- **Related flows:** 14, 15

### 12. Quiz Results
- **Platform:** Both
- **User role:** Student
- **Purpose:** Show score and per-question correctness; gate course completion.
- **Main content:** Score summary (`StatCard`, large number), pass/fail state, per-question correct/incorrect breakdown (icon + text + color).
- **Primary actions:** "Continue" (if passed → course completion) / "Retry Quiz" (if failed)
- **Secondary actions:** Review individual question explanations, if authored
- **Design system components:** StatCard, Badge (success/error variant), PrimaryButton, TonalButton
- **MVP / Post-MVP:** MVP
- **Related flows:** 15, 16, 17

### 13. Certificates List
- **Platform:** Both
- **User role:** Student
- **Purpose:** All earned certificates in one place.
- **Main content:** Grid/list of `CertificateCard`s.
- **Primary actions:** Tap a certificate → Certificate Detail
- **Secondary actions:** none
- **Design system components:** CertificateCard, EmptyState (no certificates yet — CTA to My Learning/Explore)
- **MVP / Post-MVP:** MVP
- **Related flows:** 18

### 14. Certificate Detail
- **Platform:** Both
- **User role:** Student
- **Purpose:** View and share a single certificate.
- **Main content:** Full certificate rendering (student name, course title, instructor, completion date).
- **Primary actions:** "Share" (UI-only affordance)
- **Secondary actions:** "Download"/"View full size" (if included), back to Certificates List
- **Design system components:** CertificateCard (expanded/detail variant), PrimaryButton, TextButton
- **MVP / Post-MVP:** MVP
- **Related flows:** 18

### 15. AI Tutor
- **Platform:** Both
- **User role:** Student
- **Purpose:** Conversational + quick-action learning assistant.
- **Main content:** Chat thread (`AITutorBubble`), quick-action row (`AITutorQuickAction`), message input.
- **Primary actions:** Send a message; tap a quick action
- **Secondary actions:** Scroll chat history
- **Design system components:** AITutorBubble, AITutorQuickAction, TextField (message input), LoadingState ("AI thinking" indeterminate)
- **MVP / Post-MVP:** MVP
- **Related flows:** 19, 20

### 16. Profile
- **Platform:** Both
- **User role:** Student (Instructor/Admin have their own minimal equivalents — see § C/D)
- **Purpose:** View/edit personal account info; account-level actions.
- **Main content:** `Avatar`, name, email, basic stats (courses completed, certificates).
- **Primary actions:** "Edit Profile"
- **Secondary actions:** "Settings", "Logout"
- **Design system components:** Avatar, TextField, PrimaryButton, TextButton
- **MVP / Post-MVP:** MVP
- **Related flows:** 3

### 17. Settings
- **Platform:** Both
- **User role:** Student (Instructor/Admin get the same language control inside their own Profile/Account equivalent — see § C/D — not a new screen)
- **Purpose:** Account-level preferences.
- **Main content:** Theme (Light/Dark — already system-level per DS, but an explicit override toggle may live here); **Language (v1.3, locked MVP)** — a functional Dropdown/Select (English / العربية) using the DS v1.3 `Select` component, switching UI language, text direction, and layout direction immediately, no new account or restart required (`PRODUCT_SPEC.md § 16`); account/password change; logout.
- **Primary actions:** Save changes
- **Secondary actions:** Logout
- **Design system components:** TextField, Select (DS v1.3), Toggle/Switch (added DS v1.2), PrimaryButton
- **MVP / Post-MVP:** MVP
- **Related flows:** 3, 28 (new — see § Coverage Check / `USER_FLOWS.md`)

### 18. Demo Checkout
- **Platform:** Both
- **User role:** Student
- **Purpose:** Simulated purchase confirmation — see `DEMO_PAYMENT_FLOW.md` for full detail.
- **Main content:** Course thumbnail/title/instructor, price, demo-payment notice, order summary.
- **Primary actions:** "Complete Demo Purchase"
- **Secondary actions:** Back to Course Details (cancel)
- **Design system components:** Checkout/OrderSummary (added DS v1.2), CourseCard (compact content pattern, reused within it), PrimaryButton
- **MVP / Post-MVP:** MVP
- **Related flows:** 8

### 19. Purchase Success
- **Platform:** Both
- **User role:** Student
- **Purpose:** Polished confirmation moment (portfolio-priority screen per `PRODUCT_SPEC.md § 15`).
- **Main content:** Success animation/illustration, "Payment Successful" / "You're now enrolled!" messaging.
- **Primary actions:** "Start Learning" → Course Player
- **Secondary actions:** "Back to My Learning"
- **Design system components:** SuccessState (added DS v1.2 — sibling of `EmptyState`/`ErrorState`), PrimaryButton
- **MVP / Post-MVP:** MVP
- **Related flows:** 8

---

## C. Instructor (Web-only)

### 20. Instructor Dashboard
- **Platform:** Web
- **User role:** Instructor
- **Purpose:** Instructor's home — their courses and basic stats.
- **Main content:** List of owned courses (title, status Draft/Published, enrollment count, completion rate), "Create Course" entry point.
- **Primary actions:** "Create Course"
- **Secondary actions:** Open an existing course → Course Editor — Overview
- **Design system components:** StatCard, Badge (Draft/Published state), CourseCard (compact/management variant), PrimaryButton
- **MVP / Post-MVP:** MVP
- **Related flows:** 22

### 21. Course Editor — Overview
- **Platform:** Web
- **User role:** Instructor
- **Purpose:** Edit course metadata and control publish state.
- **Main content:** Title, description, Category (Select, DS v1.3 — replaces the prior chip-based stopgap), **Level (v1.3, new field — Select: Beginner/Intermediate/Advanced)**, **Content Language (v1.3, new field — Select: the language the course content itself is authored/recorded in; independent of the instructor's or any student's UI language, see `PRODUCT_SPEC.md § 16`)**, price, thumbnail upload, current status (Draft/Published, via Toggle per approved UX decision — state also expressed as visible text, never color/position alone), publish-readiness checklist.
- **Primary actions:** "Save", "Publish" / "Unpublish" (Toggle)
- **Secondary actions:** Navigate to Curriculum tab
- **Design system components:** TextField, FileUpload (added DS v1.2, for thumbnail), Select (DS v1.3 — Category, Level, Content Language), Toggle/Switch (DS v1.2, publish state), Badge, PrimaryButton, TonalButton, Tabs (Overview/Curriculum)
- **MVP / Post-MVP:** MVP
- **Related flows:** 22, 26

### 22. Course Editor — Curriculum
- **Platform:** Web
- **User role:** Instructor
- **Purpose:** Structure the course into sections and lessons; attach the quiz.
- **Main content:** Ordered list of sections, each expandable to its ordered lessons; "Add Section"/"Add Lesson"/"Add Quiz" actions; drag-handle or up/down reorder controls.
- **Primary actions:** "Add Section", "Add Lesson"
- **Secondary actions:** Reorder, delete, "Add Quiz" / edit existing quiz
- **Design system components:** ReorderableList/DragHandle (added DS v1.2), Badge (lesson video-attached indicator), TonalButton, TextButton
- **MVP / Post-MVP:** MVP
- **Related flows:** 23, 24, 25

### 23. Lesson Editor
- **Platform:** Web
- **User role:** Instructor
- **Purpose:** Author a single lesson's content.
- **Main content:** Title, description, video upload/attach, optional resource links.
- **Primary actions:** "Save Lesson"
- **Secondary actions:** "Delete Lesson", cancel back to Curriculum
- **Design system components:** TextField, FileUpload (added DS v1.2, for lesson video), PrimaryButton, TextButton
- **MVP / Post-MVP:** MVP
- **Related flows:** 24

### 24. Quiz Editor
- **Platform:** Web
- **User role:** Instructor
- **Purpose:** Author the course's quiz questions.
- **Main content:** Ordered list of questions, each with a prompt, multiple-choice options, and a marked correct answer.
- **Primary actions:** "Add Question", "Save Quiz"
- **Secondary actions:** Reorder/delete questions
- **Design system components:** TextField, IconButton, TonalButton, PrimaryButton
- **MVP / Post-MVP:** MVP
- **Related flows:** 25

---

## D. Admin (Web-only)

### 25. Admin Dashboard
- **Platform:** Web
- **User role:** Admin
- **Purpose:** Platform-wide operational overview.
- **Main content:** `StatCard`s — total courses, total students, total instructors, published vs. draft counts.
- **Primary actions:** Navigate into Courses/Users/Instructors/Categories
- **Secondary actions:** none
- **Design system components:** StatCard
- **MVP / Post-MVP:** MVP
- **Related flows:** 27

### 26. Admin — Manage Courses
- **Platform:** Web
- **User role:** Admin
- **Purpose:** Oversee the full catalog across all instructors; moderate.
- **Main content:** Searchable/filterable table/list of all courses (title, instructor, status, enrollment count).
- **Primary actions:** Open a course (read-only student-facing view), "Unpublish"
- **Secondary actions:** Search/filter the list
- **Design system components:** DataTable (added DS v1.2), SearchField, Badge, TonalButton
- **MVP / Post-MVP:** MVP
- **Related flows:** 27

### 27. Admin — Manage Users
- **Platform:** Web
- **User role:** Admin
- **Purpose:** Basic visibility into the student population.
- **Main content:** List of students (name, email, join date, enrollment count).
- **Primary actions:** none destructive in MVP (view-only)
- **Secondary actions:** Search/filter
- **Design system components:** DataTable (added DS v1.2), SearchField, Avatar
- **MVP / Post-MVP:** MVP
- **Related flows:** 27

### 28. Admin — Manage Instructors
- **Platform:** Web
- **User role:** Admin
- **Purpose:** Basic visibility into instructors and their course counts.
- **Main content:** List of instructors (name, email, course count, published count).
- **Primary actions:** Open an instructor → filtered view of their courses (reuses Admin — Manage Courses, filtered)
- **Secondary actions:** Search/filter
- **Design system components:** DataTable (added DS v1.2), SearchField, Avatar
- **MVP / Post-MVP:** MVP
- **Related flows:** 27

### 29. Admin — Manage Categories
- **Platform:** Web
- **User role:** Admin
- **Purpose:** Keep the course category taxonomy sane (used by Explore filters and Course Editor's category field).
- **Main content:** List of categories with course counts; add/edit/remove.
- **Primary actions:** "Add Category"
- **Secondary actions:** Edit/remove an existing category (blocked or warned if courses depend on it)
- **Design system components:** TextField, PrimaryButton, TonalButton, CategoryChip (preview)
- **MVP / Post-MVP:** MVP
- **Related flows:** 27

---

## Coverage Check

- Every MVP feature in `MVP_SCOPE.md § 1` maps to at least one screen above.
- Every flow in `USER_FLOWS.md` maps to at least one screen above (cross-referenced per-screen via "Related flows").
- Every screen maps to an explicit platform per `INFORMATION_ARCHITECTURE.md`; Instructor/Admin screens (20–29) are Web-only, consistent with `USER_ROLES.md`.
- All 29 screens are MVP — no screen in this inventory is Post-MVP (Post-MVP features in `MVP_SCOPE.md § 2` accordingly have no corresponding screens here, e.g. no Forgot Password, no Wishlist, no Notifications, no Reviews-authoring screen).
- **v1.3 (English + Arabic locked as MVP):** screen count stays at **29** — no new screen was added. The functional language selector (`PRODUCT_SPEC.md § 16`) was placed inside the existing Settings/Profile screens (§ 17, and the Instructor/Admin Profile/Account equivalents) rather than as a new screen, per explicit instruction to avoid growing the screen count unless genuinely unavoidable. It was not unavoidable here.
