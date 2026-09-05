# Mentora — Product Specification

**Status:** Product planning. No screens designed, no code implemented. Design system referenced ([`../design-system/`](../design-system/)), not modified.

---

## 1. Product Vision

Mentora is a focused, cross-platform learning platform where a student can discover a course, learn through structured lessons, track real progress, test themselves with quizzes, and walk away with a certificate — with an AI Tutor available throughout to explain, summarize, and unblock them. Website, Android, and iOS are three faces of one product: the same account, the same enrolled courses, the same progress, the same certificates, everywhere.

Mentora exists as a **portfolio/demo project**: it must read as a real, coherently-architected SaaS product to a technical reviewer (GitHub) and demo well in a short video (LinkedIn) — not as a feature checklist bolted together.

## 2. Product Goals

1. Demonstrate a complete, working learning-platform user journey — discovery → purchase → learning → assessment → completion — with no dead ends.
2. Demonstrate a coherent multi-platform product built on one shared backend and data model, not three disconnected apps that happen to share a name.
3. Demonstrate real product thinking: defined roles, defined scope boundaries, a locked design system, and a documented rationale for what was deliberately left out.
4. Be achievable by a small/solo team in a realistic timeframe without real payment processing, real video infrastructure at scale, or enterprise-grade admin tooling.

## 3. Product Positioning

Mentora positions itself as a **modern, focused course platform** — closer to "one well-run course catalog with a strong learning experience" than to a marketplace like Udemy or an accredited-programs platform like Coursera. It leads with **learning experience quality** (course player, progress, quizzes, AI Tutor, certificates) rather than catalog breadth or marketplace mechanics (no reviews-as-social-proof engine, no algorithmic recommendations, no creator marketplace dynamics in MVP).

**Portfolio/demo priority is a real positioning constraint, not just an implementation note:** the flows that must look and feel strongest are discovery → course details → demo checkout → purchase success → course player → progress → quiz → AI Tutor → certificate, and the instructor dashboard as proof of a second, distinct experience. Every scope decision in [`MVP_SCOPE.md`](./MVP_SCOPE.md) is made to keep those flows polished rather than to maximize feature count.

## 4. Target Users

| Persona | Description | Primary need |
|---|---|---|
| **The learner (Student)** | Someone building a skill — a bootcamp-style learner, a career-switcher, a hobbyist. Uses whichever device is at hand (laptop at a desk, phone on commute). | Find a good course fast, learn without friction, know how far they've gotten, prove they finished it. |
| **The course creator (Instructor)** | Someone with expertise who structures it into a course — works at a desk, manages content in bulk. | A straightforward way to author and organize course content and control when it goes live. |
| **The platform operator (Admin)** | Runs the catalog — keeps categories sane, has visibility into what's on the platform and who's on it. | A simple operational overview, not a full BI suite. |
| **The evaluator (Guest / recruiter / reviewer)** | Never signs up as a student in real life, but browses the public site or watches a demo to assess the product/engineering quality. | A polished, fast, self-explanatory first impression — this persona is why "Portfolio/Demo Priority" is a first-class positioning constraint, not an afterthought. |

## 5. Core Value Proposition

- **For students:** one connected place to learn — courses, progress, quizzes, certificates, and an AI Tutor that understands what lesson they're on — available on Web, Android, and iOS with the same account and the same progress.
- **For instructors:** a simple, structured way to turn expertise into a published course without needing to touch the student-facing apps or backend directly.
- **For the platform (and the portfolio narrative):** proof that a small, well-scoped product can still be architected the way a real scalable SaaS product would be — shared backend, shared data model, one design system, role-based access, and a deliberately bounded MVP.

## 6. Main Use Cases

1. A guest discovers Mentora, browses/searches/filters courses, and registers.
2. A student demo-purchases a course, gets enrolled, and starts learning immediately.
3. A student works through a course's lessons across sessions and devices, always resuming where they left off.
4. A student takes a quiz, sees results with correct/incorrect explained (never color-only), and completes the course.
5. A student receives and views a certificate, shareable to LinkedIn as a UI action.
6. A student asks the AI Tutor about the lesson they're currently on, or uses a quick action (Summarize, Quiz me, etc.).
7. A student follows a curated Learning Path spanning multiple courses in sequence.
8. An instructor creates a course, structures it into sections and lessons, adds a quiz, and publishes it.
9. An admin reviews the course catalog, manages categories, and has basic visibility into users/instructors.

## 7. Major Product Features

- Authentication (register/login/logout) and student profile
- Course discovery: browse, search, filter, course details
- Demo purchase / simulated checkout and enrollment
- Course player: sections, lessons, video, resources, completion state
- Progress tracking and resume-learning
- Quizzes and quiz results
- Course completion and certificates
- Learning Paths (curated multi-course sequences)
- AI Tutor (lesson-context aware, quick actions)
- Student dashboard / My Learning
- Instructor course authoring (create, structure, publish)
- Admin catalog/user oversight

Full MVP vs. deferred breakdown: [`MVP_SCOPE.md`](./MVP_SCOPE.md).

## 8. Product Boundaries

Mentora is **not**:
- A course marketplace with many independent instructors competing, revenue-sharing, and payouts.
- A credentialing/accreditation body — certificates are a Mentora-issued completion artifact, not an accredited qualification.
- A community/social platform — no forums, no public profiles/follow graphs, no comments-as-discussion in MVP.
- A live-sessions/webinar product — all MVP content is pre-recorded/self-paced.
- A payments company — Mentora never touches real money (see [`DEMO_PAYMENT_FLOW.md`](./DEMO_PAYMENT_FLOW.md)).

## 9. What Mentora Intentionally Does NOT Solve in MVP

- Real payment processing, refunds, taxes, invoicing.
- Multi-instructor marketplace mechanics (revenue share, payouts, instructor ratings-driven ranking).
- Course reviews/ratings as a full social-proof system (a rating *value* may be displayed on a course as static/seed data for demo realism; a full review-writing/moderation flow is deferred — see `MVP_SCOPE.md`).
- Notifications (push/email/in-app).
- Discussion forums, Q&A per lesson, peer interaction.
- Offline/downloaded content on mobile.
- **Automatic translation of course content** (videos, instructor-written lesson text, resources, subtitles) — a course exists in whatever language its instructor authored it in; Mentora does not machine-translate it. **This is distinct from the Mentora product UI itself, which is bilingual (English + Arabic) starting with this MVP — see § 16.**
- Adaptive/algorithmic recommendations or an AI-generated curriculum.
- Enterprise admin (audit logs, granular permission editor, bulk operations, analytics dashboards beyond basic counts).
- Assignments, peer review, or instructor-graded work — only auto-graded quizzes.
- Real LinkedIn API integration for certificate sharing — MVP ships a UI affordance only.

## 10. Relationship Between Website, Android, iOS, Backend, and Shared Data

Mentora is **one product on one shared backend and one shared data model** — Website, Android, and iOS are three clients of the same API, not three separate products that happen to share branding:

- **Shared backend & data model:** a single source of truth for users, roles, courses, sections, lessons, enrollments, progress, quiz attempts, certificates, and demo purchases. An enrollment made via demo checkout on Web is immediately visible in "My Learning" on Android; a lesson completed on iOS updates progress seen on the Website. Detailed schema/architecture is out of scope for product planning (see `COURSE CONTENT` note below) and comes in a later technical-architecture phase.
- **Shared identity:** one account, one role, works identically across all three clients (subject to role — Instructor/Admin surfaces are Web-only by product decision, not a technical limitation of mobile — see [`USER_ROLES.md`](./USER_ROLES.md)).
- **Shared design system:** all three clients render from [Mentora Design System v1.3.1](../design-system/DESIGN_SYSTEM.md) — same tokens, same component contracts, platform-adapted only where `platform-mapping.md` and `LOCALIZATION.md` already say platform differences are appropriate (navigation pattern, native pickers, gesture handling).
- **Platform-appropriate UX divergence (allowed):** navigation shape (Sidebar+Navbar on Web vs. 5-item bottom nav on mobile, per the design system's locked IA — see [`INFORMATION_ARCHITECTURE.md`](./INFORMATION_ARCHITECTURE.md)), and Instructor/Admin being Web-only. These are product decisions, consistent with Design Rule 10 ("platform-specific UX differences are allowed where they improve usability").
- **What must never diverge:** what a feature *means* and what data it produces. "Enrolled," "lesson complete," "quiz passed," "certificate earned" mean exactly the same thing and update the same records regardless of which client triggered them.

---

## 11. AI Tutor — Product Scope

**Where it appears:** a persistent entry point on every authenticated surface — the `AI Tutor` tab in mobile bottom navigation, the `AI Tutor` item in the Web sidebar, and a contextual launch affordance from inside the Course Player (so a student mid-lesson can reach it in one action rather than navigating away).

**Global vs. lesson-context aware:** both. Opened from navigation, it's a general assistant scoped to the student's own learning (their courses, progress, Learning Paths). Opened from inside a lesson (Course Player), it carries that lesson's context automatically, so "Explain this lesson" and "Summarize" act on the lesson the student was just watching without them having to specify it.

**MVP capabilities:**
- Conversational Q&A about the current lesson/course.
- Quick actions (fixed set, matching the design system's `AITutorQuickAction`): *Explain this lesson*, *Summarize*, *Give me an example*, *Quiz me*, *What should I learn next?*.
- "What should I learn next?" recommends within the student's already-enrolled courses/Learning Paths — not a platform-wide recommendation engine.
- "Quiz me" generates a short, informal practice prompt distinct from the course's real, graded `Quiz` (see § 14) — practice only, not recorded as a quiz attempt or counted toward completion.
- Persists chat history per student (so returning to AI Tutor shows the prior conversation) — no cross-student sharing.

**Explicitly out of scope for MVP:**
- Voice input/output.
- Proactive/unprompted messages (e.g., AI Tutor never initiates — always student-triggered).
- Grading or generating the course's actual graded quizzes.
- Access to content outside the student's own enrollments.
- Multi-turn agentic actions (e.g., AI Tutor cannot enroll the student in a course or modify their progress — read/explain only).
- Model/provider integration itself — this document defines product behavior only; no AI API is integrated during product planning (see Scope Control).

**Language (v1.3 addition):** the AI Tutor *interface* — placeholder text, the empty/welcome message, error/retry copy, the "thinking" state, and the five quick-action labels — is localizable and ships an Arabic equivalent alongside English, same as every other MVP UI surface (§ 16). Whether the AI's own *responses* automatically follow the student's UI language (vs. matching the language of their question, or the course's content language) is a technical/AI-behavior question deferred to AI architecture — not decided here. For this planning pass, assume the AI Tutor chat *interface* supports both languages; response-language behavior is out of scope until AI integration is designed.

## 12. Learning Paths — Product Scope

A Learning Path is a **curated, ordered sequence of existing courses** authored by Mentora (conceptually, an Admin/curation function — not an MVP student- or instructor-authoring feature), presented as a single followable track with its own progress.

**Example:**

```
Android Developer (Learning Path)
  1. Kotlin Basics
  2. Object-Oriented Programming
  3. Coroutines
  4. Jetpack Compose
  5. Networking
  6. Final Project
```

**MVP model:**
- A Learning Path has a title, description, ordered list of courses, and an overall completion state derived from the completion state of its member courses (no separate Learning-Path-only content).
- A student "follows" a Learning Path, which is a lightweight commitment (visible in My Learning) rather than a purchase — enrollment/purchase still happens per course.
- Progress on a Learning Path = how many of its member courses are completed, shown as a simple fraction/progress bar (`ProgressBar` component), not a weighted or adaptive score.
- Courses within a path are presented in a recommended order but a student is not technically blocked from taking them out of order in MVP (no prerequisite-enforcement engine).

**Explicitly out of scope for MVP:** algorithmic/personalized path generation, branching paths, prerequisite gating/locking, student- or instructor-authored paths (paths are curated content, MVP-equivalent to Admin-managed seed data).

## 13. Certificates — Product Scope

**Trigger:** a certificate becomes available the moment a course's completion conditions are met — MVP definition of "complete" is *all lessons marked complete* **and**, if the course includes one, *its quiz passed* (see § 14 for pass criteria).

**Flow:** Course becomes "Completed" (visible in My Learning) → Certificate is generated/available → student views it (`CertificateCard` → certificate detail) → student can share it (UI affordance only — see § 8/9: no real LinkedIn API integration in MVP) or download/view a shareable image/PDF-style rendering.

**MVP certificate content:** student name, course title, instructor name, completion date, a Mentora certificate identifier (for visual authenticity — not a verifiable public credential system in MVP).

**Explicitly out of scope for MVP:** public certificate verification pages/URLs, real LinkedIn "Add to Profile" API integration, accreditation/CE-credit mechanics, revocation.

## 14. Course Content Model (Conceptual)

For product-planning purposes only — this is **not** a backend schema (that comes in a later technical-architecture phase):

```
Course
 ├─ metadata (title, description, category, content language, instructor, thumbnail, price, publish state)
 ├─ Section (ordered)
 │    └─ Lesson (ordered)
 │         ├─ video
 │         ├─ title
 │         ├─ description
 │         ├─ resources (optional links/files)
 │         └─ completion state (per student)
 └─ Quiz (0 or 1 per course in MVP — attached at the course level, taken after all lessons are complete)
      └─ Questions (multiple-choice, single-correct — see COMPONENTS.md § Quiz System)
```

**MVP simplifications (documented deliberately):**
- One quiz per course (not per section/lesson) — keeps authoring and the completion rule simple.
- Multiple-choice, single-correct-answer questions only (matches the already-locked `COMPONENTS.md § Quiz System` answer-option states: Default/Selected/Correct/Incorrect/Disabled).
- A course's publish state is binary (Draft / Published) — no scheduled publishing, no versioning of published content.
- **Content language (v1.3 addition) is a metadata field on the course, conceptually separate from the Mentora product UI's language** — see § 16 for the full distinction. A course has exactly one content language in MVP (no dual-language/subtitled course model yet); Instructor sets it when creating the course (Course Editor's Content Language field, `../ux/INSTRUCTOR_ADMIN_UX.md`).

---

## 15. Portfolio/Demo Priority

Cross-referenced from § 3. When two implementation choices are otherwise equal, the one that produces a better demo (visually complete, no dead ends, short and satisfying interactions) wins — **without** compromising the product structure defined in this document or the locked design system. Concretely, the flows in § 6 are the ones a LinkedIn demo video or a GitHub reviewer's first click-through is expected to exercise end-to-end without hitting a "coming soon."

---

## 16. Language & Localization — Product Scope *(locked MVP requirement)*

**Mentora MUST support both English and العربية (Arabic) in the first MVP.** This is not Post-MVP, and not "RTL readiness" in the abstract — the product UI must be functionally usable end-to-end in either language, with English → LTR and Arabic → RTL, across **Public Website, Student Website, Android, iOS, Instructor Web, and Admin Web** alike. This supersedes the earlier framing in `MVP_SCOPE.md § 2` that treated multi-language UI as Post-MVP — that entry has been corrected (§ `MVP_SCOPE.md`).

The [Mentora Design System](../design-system/DESIGN_SYSTEM.md) has been built RTL/logical-properties-correct since v1.0/v1.1 (`../design-system/LOCALIZATION.md`); this requirement makes that architecture load-bearing rather than aspirational, and adds the pieces that were still missing (formal Arabic font tokens, a real Dropdown/Select component for the language picker, locale-aware formatting guidance — see `../design-system/CHANGELOG.md` v1.3.0).

### Language Selector

- A **functional** language selector is exposed in MVP — never hidden, never a "Coming Soon" placeholder.
- **Location:** inside the existing Settings/Profile experience (`SCREEN_INVENTORY.md` screen 17, Settings) — not a separate screen. No new screen was introduced for this; the Settings screen already exists and gains one field.
- **Control:** the new [Dropdown/Select component](../design-system/COMPONENTS.md#select--dropdown-v13) (`design-system v1.3`), offering exactly two options: **English** and **العربية**.

### Language Switching Behavior

Switching language (either direction) updates, immediately and together:
1. UI strings (every localizable string — see "Localized UI Strings" below).
2. Text direction (LTR ↔ RTL).
3. Layout direction where semantically appropriate (`../design-system/LOCALIZATION.md §§ 1–3` — logical properties, not blind mirroring; the VideoPlayer scrubber exception and non-directional icons still apply identically in both languages).
4. Locale-aware formatting of dates, numbers, durations, and prices (`../design-system/LOCALIZATION.md § 7`).

Switching languages **never requires a new account** and never signs the user out.

### First-Launch Language (conceptual — not implemented here)

1. Detect the device/browser's preferred language.
2. If Arabic is supported and preferred, default to Arabic.
3. If English is preferred, default to English.
4. Otherwise, fall back to English.

The technical mechanism (which API, where the detection runs) is a Technical Architecture decision, out of scope for this document — this section fixes the *product behavior*, not the implementation.

### Language Preference Persistence (conceptual — not implemented here)

- **Guest:** language preference is stored locally on the client (persists across sessions on that device, not across devices).
- **Signed-in Student/Instructor/Admin:** the preference may additionally be stored with the account/profile, so it follows the user across devices once they're signed in on more than one. The exact persistence architecture (client-only vs. profile-synced, and how the two reconcile) is a Technical Architecture decision.

### Course Content Language Is a Separate Concept

**Application UI language and course content language are different, independently-set values.** A Student may use the Mentora UI in Arabic while taking a course whose content (video, instructor-written lesson text) is in English — this must work, and is not a contradiction or an edge case. Course content has its own `content language` metadata field (§ 14) set by the Instructor at course-creation time (English or Arabic in MVP, via the new Select component in Course Editor — `../ux/INSTRUCTOR_ADMIN_UX.md`). **Mentora does not automatically translate course content** (video, lesson text, resources, subtitles) between languages — content language is descriptive metadata (so it can be shown/filtered), not a translation feature.

### Localized UI Strings — Scope

Every user-facing **system UI** string is localizable in principle (actual translation files are a Technical Architecture/Implementation concern, not decided here): navigation labels, buttons, form labels/placeholders, error messages, success messages, empty-state copy, loading messages, the Quiz system's UI chrome (not quiz *content*, which is course content), Checkout UI (see `DEMO_PAYMENT_FLOW.md`), Certificate system UI (not the certificate's *recorded* course title/name, which reflects the record itself), AI Tutor's interface chrome (§ 11), and Instructor/Admin controls. Future implementation avoids hardcoded English strings in any of these areas — this is a locked constraint on how the eventual codebase is structured, not a suggestion.

### Locale-Aware Formatting

Dates, numbers, durations, and demo prices format per the active UI locale rather than one hardcoded English presentation — full detail and the numeral-system decision (Western Arabic numerals in both UI languages) are in `../design-system/LOCALIZATION.md § 7`. The formatting library/API is a Technical Architecture decision.

### Accessibility Applies Identically in Both Languages

Every existing Mentora accessibility requirement (screen readers, browser zoom, Android font scaling, iOS Dynamic Type, keyboard navigation, reduced motion, high text scaling, long localized content) holds in Arabic exactly as it does in English — confirmed, not newly created, in `../design-system/ACCESSIBILITY.md § 12`. Accessibility labels themselves are localizable strings, same as any other UI string above.
