# Mentora — User Flows

27 MVP flows. Screen names match [`SCREEN_INVENTORY.md`](./SCREEN_INVENTORY.md) and the structure in [`INFORMATION_ARCHITECTURE.md`](./INFORMATION_ARCHITECTURE.md). Each flow: entry point, preconditions, happy path, alternate paths, result, screens involved.

---

### 1. Registration

- **Entry point:** "Get Started"/"Register" from Landing, Navbar, or the enroll-gate on Course Details.
- **Preconditions:** Not authenticated.
- **Happy path:** Open Register → enter name, email, password → submit → account created, Student role assigned → auto-logged-in → land on Dashboard/Home.
- **Alternate paths:** Email already registered → inline error, offer Login link. Validation error (weak password, malformed email) → inline field error. Arrived via enroll-gate → after registration, redirected back into the Demo Purchase flow (§ 8) instead of Dashboard.
- **Result:** New Student account, authenticated session.
- **Screens:** Register, Dashboard/Home (or Demo Checkout if redirected).

### 2. Login

- **Entry point:** "Login" from Navbar/mobile pre-auth stack, or the enroll-gate.
- **Preconditions:** Not authenticated; has an existing account.
- **Happy path:** Open Login → enter email/password → submit → authenticated → land on Dashboard/Home (or role-appropriate landing: Instructor Dashboard / Admin Dashboard).
- **Alternate paths:** Wrong credentials → inline error, no field-specific hint (security). Arrived via enroll-gate → redirected into Demo Purchase (§ 8) after login. *(No "Forgot password?" branch in MVP — password recovery is Post-MVP; see `MVP_SCOPE.md § 5`.)*
- **Result:** Authenticated session, role-appropriate landing screen.
- **Screens:** Login, role-appropriate Dashboard/Home.

### 3. Logout

- **Entry point:** Logout action in Profile (Web: Sidebar/account menu; Mobile: Profile screen).
- **Preconditions:** Authenticated.
- **Happy path:** Tap Logout → confirm (lightweight `AppDialog` confirmation) → session ended → returned to Landing (Web) / pre-auth stack (Mobile).
- **Alternate paths:** Cancel confirmation → no change.
- **Result:** Unauthenticated (Guest) state.
- **Screens:** Profile, Landing/Login (post-logout destination).

### 4. Browse Courses

- **Entry point:** Explore (Navbar/Sidebar/bottom nav), or "Explore Courses" CTA on Landing.
- **Preconditions:** None (Guest or Student).
- **Happy path:** Open Explore → scroll/paginate the course grid → tap a `CourseCard` → Course Details.
- **Alternate paths:** Empty catalog state (`EmptyState`) — unlikely in practice but defined. Network failure → `ErrorState` with retry.
- **Result:** Student views the catalog; may proceed to Course Details.
- **Screens:** Explore, Course Details.

### 5. Search Courses

- **Entry point:** `SearchField` within Explore (or Navbar search on Web).
- **Preconditions:** None.
- **Happy path:** Type a query → results filter live or on submit → tap a result → Course Details.
- **Alternate paths:** No results → `EmptyState` ("No courses match — try a different search"). Query cleared → returns to full catalog.
- **Result:** Filtered course list, or navigation into a specific course.
- **Screens:** Explore (search state).

### 6. Filter Courses

- **Entry point:** Filter affordance within Explore (category/level/price chips or a filter panel using `CategoryChip`).
- **Preconditions:** None.
- **Happy path:** Select one or more filters → catalog updates → clear filters to reset.
- **Alternate paths:** Filter + search combined → both applied. No results after filtering → `EmptyState` with a "Clear filters" action.
- **Result:** Filtered course list.
- **Screens:** Explore (filtered state).

### 7. View Course Details

- **Entry point:** Any `CourseCard` tap (Explore, My Learning, Learning Path Details, Home recommendations).
- **Preconditions:** None.
- **Happy path:** Land on Course Details → view thumbnail, title, instructor, price, curriculum outline, rating (seed data) → decide to enroll or leave.
- **Alternate paths:** Already enrolled → primary action is "Continue Learning" (→ Course Player) instead of "Enroll"/"Buy". Not authenticated + taps enroll → routed into Login/Register first (§ 1/2), then continues into Demo Purchase.
- **Result:** Informed decision to enroll, continue, or leave.
- **Screens:** Course Details, (branch into Login/Register, Demo Checkout, or Course Player).

### 8. Demo Purchase / Simulated Checkout

Full detail in [`DEMO_PAYMENT_FLOW.md`](./DEMO_PAYMENT_FLOW.md); summarized here for flow completeness.

- **Entry point:** "Enroll"/"Buy Course" on Course Details.
- **Preconditions:** Authenticated Student; not already enrolled.
- **Happy path:** Course Details → Demo Checkout (order summary, demo-payment notice) → "Complete Demo Purchase" → brief processing state → Purchase Success ("Payment Successful" / "You're now enrolled!") → "Start Learning" → Course Player.
- **Alternate paths:** Not authenticated → routed through Login/Register first (§ 1/2). Optional simulated failure state → "Demo checkout could not be completed. Try again." → retry returns to Demo Checkout.
- **Result:** Enrollment created; student can access course content.
- **Screens:** Course Details, Demo Checkout, Purchase Success, Course Player.

### 9. Enroll in a Course

Enrollment in MVP is *only* reached through Demo Purchase (§ 8) — there is no separate free-enrollment path, since all MVP courses go through the same demo-checkout step for consistency (see `PRODUCT_SPEC.md § 14`, `DEMO_PAYMENT_FLOW.md`). This flow entry exists for completeness of the requested list and is functionally identical to § 8's result:

- **Entry point:** Successful completion of § 8.
- **Preconditions:** Successful demo purchase.
- **Happy path:** Enrollment record created → course appears in My Learning → progress initialized at 0%.
- **Alternate paths:** None (system-triggered, not a separate user action).
- **Result:** Course accessible in My Learning and Course Player.
- **Screens:** (system state change; surfaced in) My Learning, Course Player.

### 10. Start a Course

- **Entry point:** "Start Learning" (post-purchase), or "Continue"/first-time open from My Learning/Course Details.
- **Preconditions:** Enrolled.
- **Happy path:** Open Course Player → first section/lesson loads → video begins → progress begins tracking.
- **Alternate paths:** None notable.
- **Result:** Student is actively in the learning surface.
- **Screens:** Course Player.

### 11. Complete a Lesson

- **Entry point:** Within Course Player, watching a lesson.
- **Preconditions:** Enrolled, lesson in progress.
- **Happy path:** Lesson video finishes (or student manually marks complete) → lesson marked complete → progress updates → player auto-advances to next lesson.
- **Alternate paths:** Manual "Mark Complete" before video ends (student already knows the material). Last lesson in course + quiz exists → prompted into Quiz (§ 14). Last lesson + no quiz → course completes directly (§ 17).
- **Result:** Lesson completion recorded; course/section progress updated.
- **Screens:** Course Player.

### 12. Resume Learning

- **Entry point:** "Continue" on a `CourseProgressCard` (My Learning, Home/Dashboard "Continue learning" module).
- **Preconditions:** Enrolled, course in progress (not yet complete).
- **Happy path:** Tap Continue → Course Player opens directly at the last unfinished lesson, at the last playback position if mid-video.
- **Alternate paths:** Course already complete → "Continue" is replaced by "Review"/"View Certificate" (see § 18).
- **Result:** Student resumes exactly where they left off, on any device.
- **Screens:** My Learning / Dashboard/Home, Course Player.

### 13. Course Progress Update

System-driven, surfaced continuously rather than a discrete user action:

- **Entry point:** Any lesson-completion or quiz-submission event.
- **Preconditions:** Enrolled.
- **Happy path:** Event occurs → per-lesson, per-section, and per-course progress recalculated → `ProgressBar` values update wherever shown (Course Player, My Learning, Dashboard/Home, Learning Path Details).
- **Alternate paths:** None (deterministic recalculation).
- **Result:** Progress is always consistent across every screen and every device (shared backend, `PRODUCT_SPEC.md § 10`).
- **Screens:** Course Player, My Learning, Dashboard/Home, Learning Path Details.

### 14. Take a Quiz

- **Entry point:** Automatically prompted after the last lesson in a course that has a quiz (§ 11), or "Take Quiz" from Course Player/My Learning if revisiting.
- **Preconditions:** All lessons in the course complete.
- **Happy path:** Quiz opens → question 1 of N shown (`QuizCard` + progress indicator) → select an answer → next question → repeat → final question → "Submit Quiz".
- **Alternate paths:** Student leaves mid-quiz → answers preserved, can resume (not force-reset). Attempts to submit with unanswered questions → inline prompt to complete remaining questions first.
- **Result:** All questions answered, ready to submit.
- **Screens:** Quiz.

### 15. Submit Quiz

- **Entry point:** "Submit Quiz" action at the end of § 14.
- **Preconditions:** All questions answered.
- **Happy path:** Submit → answers graded → attempt recorded → routed to Quiz Results.
- **Alternate paths:** None (grading is immediate and automatic — MVP has no manual grading).
- **Result:** A recorded quiz attempt with a score.
- **Screens:** Quiz → Quiz Results.

### 16. View Quiz Result

- **Entry point:** Automatic, immediately after § 15.
- **Preconditions:** A quiz was just submitted (or being reviewed later from My Learning/Course Player).
- **Happy path:** Quiz Results shows score, pass/fail state, and a per-question correct/incorrect breakdown (icon + text + color, never color-only, per the locked design system's `COMPONENTS.md § Quiz System`) → "Continue" routes into course completion (§ 17) if passed.
- **Alternate paths:** Failed (below pass threshold) → "Retry Quiz" action returns to § 14; course is not marked complete until passed.
- **Result:** Student understands their performance; course completion is unlocked if passed.
- **Screens:** Quiz Results.

### 17. Complete a Course

- **Entry point:** Automatic — triggered when all lessons are complete **and** (if the course has a quiz) the quiz is passed.
- **Preconditions:** All completion conditions met (`PRODUCT_SPEC.md § 13`).
- **Happy path:** Course status flips to "Completed" → certificate becomes available → student is shown a completion confirmation (within Quiz Results or Course Player, not a separate screen) with a "View Certificate" action.
- **Alternate paths:** Course has no quiz → completion triggers directly off the last lesson, no quiz gate.
- **Result:** Course marked complete in My Learning; certificate available.
- **Screens:** Quiz Results or Course Player (completion confirmation), My Learning.

### 18. Receive / View Certificate

- **Entry point:** "View Certificate" from the completion confirmation (§ 17), or Certificates list at any later time.
- **Preconditions:** Course completed.
- **Happy path:** Certificate Detail opens → shows student name, course title, instructor, completion date → "Share" (UI-only affordance, e.g. LinkedIn-styled share button — no real API call) / view full-size.
- **Alternate paths:** None notable in MVP (no revocation/expiry).
- **Result:** Student has a viewable, shareable certificate artifact.
- **Screens:** Certificates List, Certificate Detail.

### 19. Ask AI Tutor About a Lesson

- **Entry point:** AI Tutor affordance inside Course Player.
- **Preconditions:** Enrolled, viewing a lesson.
- **Happy path:** Open AI Tutor (carries current lesson context) → type a question → AI responds referencing the current lesson.
- **Alternate paths:** Question outside the student's enrolled content → AI Tutor answers only from what it has context on (`PRODUCT_SPEC.md § 11` — no access outside own enrollments).
- **Result:** Student gets a contextual answer without leaving their learning session conceptually (AI Tutor is one tap away).
- **Screens:** Course Player, AI Tutor.

### 20. Use AI Tutor Quick Actions

- **Entry point:** AI Tutor screen, quick-action row (`AITutorQuickAction`).
- **Preconditions:** AI Tutor open; ideally lesson-context available (global-context still works, with reduced specificity).
- **Happy path:** Tap a quick action (*Explain this lesson / Summarize / Give me an example / Quiz me / What should I learn next?*) → AI responds accordingly.
- **Alternate paths:** "Quiz me" → generates an informal practice prompt, distinct from and not recorded as a real `Quiz` attempt (`PRODUCT_SPEC.md § 11`). "What should I learn next?" with no enrollments → AI suggests browsing Explore/Learning Paths instead.
- **Result:** Fast, structured help without free-typing a question.
- **Screens:** AI Tutor (from Sidebar/bottom nav or from within Course Player).

### 21. View / Follow a Learning Path

- **Entry point:** Learning Paths (Sidebar/Explore-nested), or a `LearningPathCard` surfaced on Home/Dashboard.
- **Preconditions:** None to view; authenticated to follow.
- **Happy path:** Browse Learning Paths → open a path → see its ordered course list and description → "Follow Path" → path appears in My Learning with path-level progress → tap a member course → Course Details (then Demo Purchase/§ 8 if not yet enrolled in that specific course).
- **Alternate paths:** Not authenticated + taps Follow → routed through Login/Register first. Already following → action becomes "Unfollow"/already-following state, or simply hidden since it's already in My Learning.
- **Result:** Path tracked in My Learning; progress reflects completion of its member courses.
- **Screens:** Learning Paths, Learning Path Details, Course Details, My Learning.

### 22. Instructor Creates a Course

- **Entry point:** "Create Course" from Instructor Dashboard.
- **Preconditions:** Authenticated Instructor.
- **Happy path:** Create Course → enter title, description, category, price, thumbnail → save → course created in **Draft** state → routed to Course Editor — Curriculum to add content.
- **Alternate paths:** Save incomplete metadata → allowed (Draft can be incomplete; Published cannot — see § 26). Cancel → course discarded if never saved, or remains Draft if already saved once.
- **Result:** A new Draft course, owned by the Instructor.
- **Screens:** Instructor Dashboard, Course Editor — Overview, Course Editor — Curriculum.

### 23. Instructor Adds Sections

- **Entry point:** Course Editor — Curriculum, for a Draft (or Published, for later edits) course.
- **Preconditions:** Authenticated Instructor, owns the course.
- **Happy path:** "Add Section" → name it → section appears in the curriculum outline, ready for lessons → reorder via drag or up/down controls.
- **Alternate paths:** Delete a section (with a confirmation if it contains lessons). Reorder sections.
- **Result:** Course curriculum structure (sections) defined.
- **Screens:** Course Editor — Curriculum.

### 24. Instructor Adds Lessons

- **Entry point:** "Add Lesson" within a section, Course Editor — Curriculum.
- **Preconditions:** At least one section exists.
- **Happy path:** Add Lesson → Lesson Editor opens → enter title, description, upload/attach video, add optional resources → save → lesson appears in the section, reorderable.
- **Alternate paths:** Save a lesson without video attached → allowed in Draft, flagged as incomplete for Publish (§ 26). Delete/reorder lessons.
- **Result:** Lesson content defined within a section.
- **Screens:** Course Editor — Curriculum, Lesson Editor.

### 25. Instructor Adds Quiz

- **Entry point:** "Add Quiz" from Course Editor — Curriculum (course-level, per `PRODUCT_SPEC.md § 14`: one quiz per course).
- **Preconditions:** Course exists (quiz can be added before or after lessons, but is logically taken after all lessons by students).
- **Happy path:** Quiz Editor opens → add questions (prompt + multiple-choice options + mark correct answer) → reorder/delete questions → save.
- **Alternate paths:** Save an incomplete quiz (e.g., a question with no marked correct answer) → allowed in Draft, flagged for Publish (§ 26).
- **Result:** A quiz attached to the course, ready for students once published.
- **Screens:** Course Editor — Curriculum, Quiz Editor.

### 26. Instructor Publishes a Course

- **Entry point:** "Publish" action in Course Editor — Overview.
- **Preconditions:** Course meets minimum publish requirements (title, description, category, price, thumbnail set; at least one section with at least one lesson with video attached).
- **Happy path:** Tap Publish → validation passes → course status flips Draft → Published → course becomes visible in public Explore/search and purchasable.
- **Alternate paths:** Validation fails → inline list of what's missing, course stays Draft. "Unpublish" reverses this (course hides from Explore/search but existing enrolled students keep access — access, once granted, is never revoked by unpublishing).
- **Result:** Course is live and discoverable.
- **Screens:** Course Editor — Overview.

### 27. Admin Reviews / Manages a Course

- **Entry point:** Admin → Manage Courses.
- **Preconditions:** Authenticated Admin.
- **Happy path:** Browse/search the full course list (all instructors) → open a course to view it as a student would see it → if needed, Unpublish → instructor's course reverts to Draft, disappears from Explore (existing enrollments unaffected, per § 26).
- **Alternate paths:** No action needed on a compliant course → Admin simply browses/monitors. Manage Categories/Users/Instructors are parallel, simpler flows (list → view; category CRUD) not detailed further here — see `SCREEN_INVENTORY.md`.
- **Result:** Catalog stays within platform standards without editing instructor content directly (`USER_ROLES.md`).
- **Screens:** Admin Dashboard, Admin — Manage Courses, (read-only view into) Course Details.

---

### 28. Change UI Language *(v1.3 addition — not part of the original 27; added when English + Arabic became a locked MVP requirement, see `PRODUCT_SPEC.md § 16`)*

- **Entry point:** Settings (Student/Instructor/Admin's Profile/Account equivalent — see `SCREEN_INVENTORY.md § 17`) → Language field, a Select (DS v1.3).
- **Preconditions:** None — available to Guest (a persisted local preference, no account) and every authenticated role alike.
- **Happy path:** Open the Language Select → choose English or العربية → UI strings, text direction, and layout direction update immediately, in place, without navigating away or restarting the app → for a signed-in user, the choice also saves to the account (cross-device); for a Guest, it saves locally on-device only.
- **Alternate paths:** Guest later registers/logs in → local preference is honored as the starting value for their new account setting (no forced re-selection). Course content already open in a different language than the new UI language is unaffected — course-content language and UI language are independent (`PRODUCT_SPEC.md § 16`).
- **Result:** The full app (Public Website, Student Website, Android, iOS, Instructor Web, Admin Web) renders in the chosen language; the VideoPlayer scrubber/timeline and the Mentora wordmark are the only elements that do not mirror direction.
- **Screens:** Settings.
