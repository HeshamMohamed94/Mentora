# Mentora — MongoDB Data Model

Conceptual model — collections and relationships, not implementation code. Every collection's embed-vs-reference decision follows the rule locked in [ADR-005](./adr/ADR-005-mongodb-modeling-strategy.md). Database: MongoDB, per [ADR-004](./adr/ADR-004-database-engine.md).

For each collection: identity, ownership, key fields, embed/reference decisions, indexes, lifecycle.

---

## 1. `users`

**Identity:** `_id` (ObjectId). **Ownership:** the user themself; Admin has read access, never write access to another user's credentials.

**Key fields:** `email` (unique), `passwordHash` (BCrypt), `role` (`student` | `instructor` | `admin` — exactly one, never an array, per [`../product/USER_ROLES.md`](../product/USER_ROLES.md)'s one-role-per-account rule), `name`, `avatarMediaId` (reference to `media`, nullable), `preferredLocale` (`en` | `ar`, nullable — null means "no account-level preference yet, defer to client-detected/local default"), `createdAt`.

**Embed vs. reference:** nothing unboundedly-growing is embedded here — no quiz attempts, no AI messages, no progress records. This document is read on nearly every authenticated request (authorization checks), so it must stay small and fast to read regardless of how long the account has existed.

**Indexes:** unique index on `email`; index on `role` (Admin's "Manage Users"/"Manage Instructors" list filters by role).

**Lifecycle:** created at registration. No hard delete in MVP (no account-deletion flow specified in product docs) — soft-delete/deactivation is a Post-MVP concern if ever needed.

---

## 2. `refreshTokens`

**Identity:** `_id`. **Ownership:** system-internal, tied to a `userId`.

**Key fields:** `userId` (ref → `users`), `tokenHash` (the refresh token is never stored plaintext — hashed the same way a password is, since possession of it grants a new session), `familyId` (groups a chain of rotated tokens so reuse-of-a-rotated-token can revoke the whole family — see [ADR-006](./adr/ADR-006-authentication-strategy.md)), `expiresAt`, `revokedAt` (nullable), `deviceLabel` (optional, for a future "manage sessions" UI — not required MVP behavior, cheap to store now).

**Indexes:** index on `userId`; index on `tokenHash`; **TTL index on `expiresAt`** (MongoDB automatically purges expired tokens — this is the correct MongoDB-native way to keep this collection from growing unboundedly with dead tokens).

**Lifecycle:** created on login/refresh, rotated (old marked revoked, new inserted) on each refresh, deleted automatically by the TTL index after expiry, or immediately marked revoked on logout.

---

## 3. `categories`

**Identity:** `_id`. **Ownership:** Admin (create/edit/remove), read by everyone.

**Key fields:** `name` (localizable — see note below), `slug`, `courseCount` (denormalized counter, updated when a course's category changes or a course is published/deleted — acceptable denormalization since it's read far more often than written, and eventual-consistency-for-a-counter is a normal, low-risk tradeoff).

**Localization note:** category *names* shown in the catalog are content, not UI chrome — MVP models one name per category (Admin-authored, in whichever language the catalog primarily targets) rather than a full per-locale translation map, since [`../product/PRODUCT_SPEC.md § 16`](../product/PRODUCT_SPEC.md) scopes UI-string localization to system chrome, and category taxonomy is closer to content. If Admin needs to author bilingual category names, `name` becomes `{ en: string, ar: string }` — a contained schema change, not an architectural one; flagged here rather than silently decided either way.

**Indexes:** unique index on `slug`.

**Lifecycle:** Admin CRUD. Deletion blocked (service-layer check, not a Mongo-level constraint) if `courseCount > 0`, per [`../ux/INSTRUCTOR_ADMIN_UX.md`](../ux/INSTRUCTOR_ADMIN_UX.md)'s guardrail.

---

## 4. `courses`

**Identity:** `_id`. **Ownership:** the Instructor who created it (`instructorId`); Admin can change `status` only.

**Key fields:** `instructorId` (ref → `users`), `title`, `description`, `categoryId` (ref → `categories`), `level` (`beginner`|`intermediate`|`advanced`), `contentLanguage` (`en`|`ar` — metadata only, per [`../product/PRODUCT_SPEC.md § 16`](../product/PRODUCT_SPEC.md), **never** used to select UI translation), `priceDisplay` (integer minor-unit amount + currency code — display-only demo value, never wired to billing, per [`../product/DEMO_PAYMENT_FLOW.md § 5`](../product/DEMO_PAYMENT_FLOW.md)), `thumbnailMediaId` (ref → `media`), `status` (`draft`|`published`), `ratingSeed` (a static display value per [`../product/PRODUCT_SPEC.md § 9`](../product/PRODUCT_SPEC.md) — not computed from real reviews, MVP has no review-authoring), `sections` (embedded array — see below), `createdAt`, `updatedAt`.

**Embedded `sections[]`:** each section: `sectionId` (a stable string/UUID, not a Mongo ObjectId, since sections are addressed by clients for reorder operations and don't need to be separately queried), `title`, `order`, `lessons[]` (embedded).

**Embedded `lessons[]` (within a section):** `lessonId`, `title`, `description`, `order`, `videoMediaId` (ref → `media`, nullable while a Draft lesson has no video yet), `resources[]` (embedded — each `{ label, url }`, a small bounded list of external links, not files themselves).

**Why sections/lessons are embedded, not referenced:** per [ADR-005](./adr/ADR-005-mongodb-modeling-strategy.md) — bounded (a course realistically has single-digit sections, tens of lessons, not thousands), always authored together (Course Editor — Curriculum edits the whole tree in one session), and always read together (Course Details' curriculum outline and Course Player's lesson list both need the full tree, not one lesson in isolation). A `Course` document's realistic worst-case size (a few hundred lessons across many sections, each with a title/description/resource links but no binary content) stays comfortably within MongoDB's 16MB document limit by orders of magnitude.

**What's deliberately NOT embedded here:** per-student completion state (→ `progress`, § 6 — a `Course` document must not grow per enrolled student), quiz content (→ `quizzes`, § 7 — a distinct, independently-versioned artifact), reviews (Post-MVP, out of scope entirely).

**Indexes:** index on `instructorId` (Instructor Dashboard's course list); index on `status` + `categoryId` (Explore's filtered browse); **text index** on `title` + `description` (keyword search, per [ADR-007](./adr/ADR-007-api-style.md)); index on `status` alone (Explore's default published-only view).

**Lifecycle:** created Draft by an Instructor → edited freely while Draft → validated and flipped to Published (validation rule: title/description/category/price/thumbnail set, ≥1 section with ≥1 lesson with a video, per [`../product/USER_FLOWS.md § 26`](../product/USER_FLOWS.md)) → Admin or owning Instructor can Unpublish (reverts to Draft-equivalent visibility; existing `enrollments` are never revoked, per the same flow doc) → no hard delete of a Published course with active enrollments is exposed in MVP UI (not specified as needed).

---

## 5. `enrollments`

**Identity:** `_id`, unique on (`userId`, `courseId`) — a student can only be enrolled in a course once. **Ownership:** the enrolled Student (read); system-created (write, only via the enrollment service during checkout).

**Key fields:** `userId` (ref → `users`), `courseId` (ref → `courses`), `source` (`demoCheckout` — the only source in MVP, kept as an enum rather than hardcoded so a future non-checkout enrollment path, e.g. a free-course grant, doesn't require a schema change), `demoPurchaseId` (ref → `demoPurchases`), `enrolledAt`, `status` (`active` — MVP has no unenroll/refund flow, kept as an enum for forward-compatibility rather than assuming `active` is the only value forever).

**Why its own collection, not embedded in `users`:** every purchase adds one; a long-lived student's `users` document would otherwise grow with every course they ever bought — exactly the unbounded-growth pattern [ADR-005](./adr/ADR-005-mongodb-modeling-strategy.md) forbids, and `users` is read on every authenticated request, making this doubly costly.

**Indexes:** unique compound index on (`userId`, `courseId`) — this index *is* the idempotency mechanism for demo-checkout completion (§ [`API_CONTRACT.md § 5`](./API_CONTRACT.md)); index on `userId` alone (My Learning's enrollment list); index on `courseId` (Instructor's enrollment-count aggregation).

**Lifecycle:** created inside the demo-checkout transaction (§ [`BACKEND_ARCHITECTURE.md § 4`](./BACKEND_ARCHITECTURE.md)). Never deleted in MVP.

---

## 6. `progress`

**Identity:** `_id`, unique on (`userId`, `courseId`) — one progress document per enrollment. **Ownership:** the enrolled Student (their own activity writes it, via server-validated endpoints — never a client-declared "I completed this" without the server independently accepting the specific transition); Instructor sees only the aggregate (completion rate), never this document directly.

**Key fields:** `userId`, `courseId`, `completedLessonIds` (a set/map keyed by `lessonId` → `completedAt` — **bounded by the course's own lesson count**, which is itself bounded per § 4, so this is safe to embed as a map rather than a growing array of events), `currentLessonId` (resume pointer), `currentPositionSeconds` (last known playback position within `currentLessonId`, for resume-mid-video), `quizPassed` (nullable — null if the course has no quiz or it hasn't been attempted/passed yet), `completionPercent` (denormalized, recalculated server-side on every write — never client-supplied), `courseCompletedAt` (nullable, set the moment completion conditions are met), `updatedAt`.

**Why a map keyed by lesson ID, not an append-only event log:** an event log (`[{lessonId, completedAt}, {lessonId, completedAt}, ...]`) would still be bounded by lesson count *if* re-completions never append a new entry, but a keyed map is simpler to reason about, cheaper to update (`$set` a single map key vs. deduplicate-then-append), and equally bounded — chosen for simplicity, not because the event-log alternative was unsafe.

**Why its own collection, not embedded in `enrollments`:** written far more frequently (every lesson completion, every playback-position heartbeat) than `enrollments` (written once, at purchase) — separating write-hot data from write-once data avoids unnecessary write contention/versioning conflicts on the same document for two very differently-paced concerns.

**Indexes:** unique compound index on (`userId`, `courseId`); index on `userId` alone (My Learning, Dashboard "Continue learning").

**Lifecycle:** created (all fields zeroed/empty) at the same moment as its `Enrollment`, inside the same transaction. Updated on every lesson-completion and quiz-submission event. Read on nearly every learning-surface screen.

---

## 7. `quizzes`

**Identity:** `_id`, unique on `courseId` (one quiz per course, per [`../product/PRODUCT_SPEC.md § 14`](../product/PRODUCT_SPEC.md)). **Ownership:** the course's Instructor (author), read by enrolled Students (with `isCorrect` stripped — see below).

**Key fields:** `courseId` (ref → `courses`), `questions[]` (embedded — bounded, authoring-time list, realistically under ~50 questions), each question: `questionId`, `prompt`, `options[]` (each `{ optionId, text, isCorrect }`), `order`.

**Why embedded:** authored as a whole in one Quiz Editor session, read as a whole (a student takes the entire quiz in one sitting), and bounded by realistic question count — exactly [ADR-005](./adr/ADR-005-mongodb-modeling-strategy.md)'s embed criteria.

**Security-critical projection rule:** the backend **never** serializes `isCorrect` to a student taking the quiz — the `quiz` module's service layer returns a stripped DTO (`options[].isCorrect` omitted entirely, not just hidden client-side) for `GET` requests used while taking a quiz; the full document (with `isCorrect`) is only ever returned to the owning Instructor's Quiz Editor or used internally for grading. This directly satisfies the brief's "do not expose correct answers insecurely before submission."

**Indexes:** unique index on `courseId`.

**Lifecycle:** created/edited by the Instructor via Quiz Editor. Editing an already-Published course's quiz is allowed (matches [`../product/USER_FLOWS.md § 25`](../product/USER_FLOWS.md), no versioning of published content specified) — a Student's in-progress attempt (§ 8) captures the question/option snapshot at submission time regardless, so a mid-edit doesn't corrupt an in-flight attempt's grading.

---

## 8. `quizAttempts`

**Identity:** `_id` (one per submission — not unique on user+quiz, since retries are allowed and each is a new document). **Ownership:** the Student who submitted it.

**Key fields:** `userId`, `quizId` (ref → `quizzes`), `courseId` (denormalized for query convenience — avoids a join just to answer "this student's attempts for this course"), `answers[]` (`{ questionId, selectedOptionId }`), `score`, `passed` (boolean, threshold defined by the `quiz` module — MVP default: a simple percentage threshold, e.g. 70%, configurable per course later if ever needed), `submittedAt`.

**Why its own collection, never embedded in `quizzes` or `progress`:** unboundedly growing (every retry, by every student, forever) — the textbook case [ADR-005](./adr/ADR-005-mongodb-modeling-strategy.md) exists to prevent. `progress.quizPassed` is the *derived, current* pass state; `quizAttempts` is the full history.

**Indexes:** compound index on (`userId`, `quizId`) sorted by `submittedAt` descending (find latest/best attempt fast).

**Lifecycle:** append-only. Never updated or deleted after creation — an attempt is an immutable historical record.

---

## 9. `learningPaths`

**Identity:** `_id`. **Ownership:** Admin (curated content, per [`../product/PRODUCT_SPEC.md § 12`](../product/PRODUCT_SPEC.md) — "conceptually an Admin/curation function," modeled here as Admin-managed seed data with no dedicated authoring UI in MVP, consistent with `../product/MVP_SCOPE.md`).

**Key fields:** `title`, `description`, `courseIds[]` (ordered, embedded array of `courseId` references — the *order* is the point, so this is an ordered reference list, not a set), `createdAt`.

**Why `courseIds` is a reference array, not an embedded copy of each course:** a `LearningPath` must always reflect the *current* state of its member courses (title, thumbnail, publish status) — embedding a snapshot would drift the moment a course is edited. The array of IDs itself is small and bounded (a handful of courses per path), so embedding *the list of IDs* (not the courses themselves) is safe and exactly matches [ADR-005](./adr/ADR-005-mongodb-modeling-strategy.md)'s "curated, admin-authored, small ordered list" embed case.

**Indexes:** none beyond `_id` needed at MVP catalog scale (few paths, browsed in full).

**Lifecycle:** Admin-managed seed data for MVP (no dedicated Learning-Path-authoring screen exists in the 29 — confirmed against [`../product/SCREEN_INVENTORY.md`](../product/SCREEN_INVENTORY.md)). Seeded via `infra/docker/mongo-init` for local/demo data.

---

## 10. `learningPathFollows`

**Identity:** `_id`, unique on (`userId`, `pathId`). **Ownership:** the following Student.

**Key fields:** `userId`, `pathId` (ref → `learningPaths`), `followedAt`.

**Why its own collection, not an array on `users` or `learningPaths`:** same reasoning as `enrollments` vs. `users` — a "followed paths" array on `users` would grow indefinitely over a long-lived account's lifetime, on a document read every request.

**Indexes:** unique compound index on (`userId`, `pathId`); index on `userId` (My Learning's followed-paths list). Path-level progress is **derived**, not stored — computed on read from `courseIds` (§ 9) joined against the student's `enrollments`/`progress` completion state, since it's cheap to compute at read time and storing it would mean keeping a third place in sync every time a member course's progress changes.

**Lifecycle:** created on Follow, no unfollow flow required by product docs in MVP (not blocking — `USER_FLOWS.md § 21` mentions "already following → action becomes Unfollow" as a possibility; if implemented, this is a simple delete of the doc).

---

## 11. `certificates`

**Identity:** `_id` — this is also the **public-facing certificate identifier** shown on the artifact (per [`../product/PRODUCT_SPEC.md § 13`](../product/PRODUCT_SPEC.md): "a Mentora certificate identifier for visual authenticity"), formatted as a short human-presentable code, not a raw ObjectId, at the API/presentation layer. **Ownership:** the Student who earned it (immutable once issued).

**Key fields:** `userId`, `courseId`, `issuedAt`, and a **denormalized snapshot**: `studentNameSnapshot`, `courseTitleSnapshot`, `instructorNameSnapshot`, `completionDateSnapshot`.

**Why the snapshot fields, duplicating data already in `users`/`courses`:** a certificate is a historical record — if a student later changes their display name, or an Instructor edits a course's title, previously-issued certificates must **not** silently change retroactively (this would be a correctness bug: "my certificate now says a different course name than the course I actually completed at the time"). Denormalizing at issuance time is the standard, correct pattern for this exact problem and is a deliberate architectural choice, not an oversight of "should reference instead."

**Indexes:** index on `userId` (Certificates List).

**Lifecycle:** created exactly once, inside the same transaction as the progress update that completes the course (§ [`BACKEND_ARCHITECTURE.md § 4`](./BACKEND_ARCHITECTURE.md)). Never updated. No revocation mechanism in MVP (explicitly out of scope, [`../product/PRODUCT_SPEC.md § 13`](../product/PRODUCT_SPEC.md)).

---

## 12. `demoPurchases`

**Identity:** `_id`. **Ownership:** the purchasing Student; system-created only.

**Key fields:** `userId`, `courseId`, `priceDisplaySnapshot` (the price shown at purchase time — display/demo value only), `completedAt`. **Explicitly absent, by design, per [`../product/DEMO_PAYMENT_FLOW.md § 4`](../product/DEMO_PAYMENT_FLOW.md):** no card data, no gateway response, no fake transaction ID, no billing address — this document exists purely to record "this student completed the simulated checkout for this course," nothing more.

**Indexes:** index on `userId`.

**Lifecycle:** created once per successful demo checkout, inside the same transaction as its resulting `Enrollment` (§ 5). Never updated.

---

## 13. `aiConversations`

**Identity:** `_id`. **Ownership:** the Student (one conversation history per student, per [`../product/PRODUCT_SPEC.md § 11`](../product/PRODUCT_SPEC.md): "persists chat history per student... no cross-student sharing").

**Key fields:** `userId` (unique — MVP is one continuous conversation per student, not multiple named threads, matching the product doc's description of a single persisted history rather than a thread-list UI), `createdAt`, `lastMessageAt` (denormalized, for potential future "recent activity" sorting — not currently surfaced in UI, cheap to maintain).

**Why messages are NOT embedded here:** unboundedly growing over the life of the account — the same reasoning as `quizAttempts`. An active student's AI Tutor usage over months would otherwise make this exact document, read every time AI Tutor opens, arbitrarily large and slow.

**Indexes:** unique index on `userId`.

**Lifecycle:** created lazily on a student's first AI Tutor message. Never deleted in MVP (no "clear history" feature specified).

---

## 14. `aiMessages`

**Identity:** `_id`. **Ownership:** the conversation's owning Student (via `conversationId`).

**Key fields:** `conversationId` (ref → `aiConversations`), `role` (`user`|`assistant`), `content`, `lessonContextId` (nullable ref → the specific lesson this message was asked in context of, if opened from Course Player), `createdAt`.

**Indexes:** compound index on (`conversationId`, `createdAt`) — fetch a conversation's history in order, paginated (see [`API_CONTRACT.md`](./API_CONTRACT.md) for cursor pagination on this endpoint specifically, since a long-lived conversation is exactly the kind of list that must never be fetched in full).

**Lifecycle:** append-only, one document per message (both user-authored and AI-authored messages are stored here uniformly). Never updated. See [`AI_TUTOR_ARCHITECTURE.md § 5`](./AI_TUTOR_ARCHITECTURE.md) for the privacy/retention boundary on this collection's content.

---

## 15. `media`

**Identity:** `_id`. **Ownership:** the uploader (`uploadedByUserId`) — an Instructor for course/lesson media, a Student for their own avatar.

**Key fields:** `uploadedByUserId`, `kind` (`courseThumbnail`|`lessonVideo`|`avatar`), `ownerRefId` (the `courseId`/`lessonId`/`userId` this media belongs to, interpreted per `kind`), `storageKey` (a server-generated, opaque key resolved against the local filesystem storage root for the MVP — see [ADR-008](./adr/ADR-008-media-storage.md); would map to an object-storage key unchanged if migrated to cloud storage later — never a full public URL stored here, since playback URLs are signed/verified per-request, see [`MEDIA_ARCHITECTURE.md`](./MEDIA_ARCHITECTURE.md)), `contentType`, `sizeBytes`, `durationSeconds` (nullable, video only), `status` (`pendingUpload`|`ready`|`failed`), `createdAt`. **Never stores the binary media content itself, and never a large video binary — content lives only in local media storage.**

**Indexes:** index on `ownerRefId` + `kind`.

**Lifecycle:** created in `pendingUpload` status when a presigned upload URL is issued, flipped to `ready` when the client confirms completion and the backend verifies the object exists in storage, `failed` if verification fails or the upload is abandoned (a periodic cleanup job — noted as a future-evolution nicety, not MVP-blocking — can garbage-collect long-stale `pendingUpload` records and their orphaned storage objects).

---

## 16. Relationship Diagram (conceptual)

```
users ──1:N── courses (instructorId)
users ──1:N── enrollments ──N:1── courses
users ──1:1── progress (per enrollment, keyed by userId+courseId)
courses ──1:0..1── quizzes ──1:N── quizAttempts ──N:1── users
users ──1:N── learningPathFollows ──N:1── learningPaths ──N:M── courses (via courseIds[])
users ──1:N── certificates ──N:1── courses
users ──1:N── demoPurchases ──N:1── courses
users ──1:1── aiConversations ──1:N── aiMessages
courses/lessons/users(avatar) ──N:1── media (via ownerRefId)
users ──1:N── refreshTokens
```

## 17. Cross-Reference to the Product Domain Vocabulary

This model uses exactly the terms [`../product/DEMO_PAYMENT_FLOW.md § 4`](../product/DEMO_PAYMENT_FLOW.md) locked for the checkout domain (`DemoPurchase`, `Enrollment` with a `source` field rather than a separate `SimulatedCheckout` collection — the flow itself has no persistent state of its own beyond the `DemoPurchase` record it produces, so it isn't modeled as a collection). No fake payment-processor vocabulary (transaction ID, card token, gateway response) appears anywhere in this schema, by design.
