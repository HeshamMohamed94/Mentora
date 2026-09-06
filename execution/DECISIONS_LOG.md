# Mentora — Implementation Decisions Log

Records implementation-time decisions that are consistent with the locked architecture but weren't fully pinned down at the architecture-doc level (e.g. "either approach is fine," or a local-environment fact discovered during implementation). This is **not** a place to record new architecture decisions that contradict locked docs — those require explicit user approval and, if accepted, an update to the relevant `architecture/adr/`.

Format: `D<n> — <date> — <decision>` with **Why** and **Impact**.

---

### D1 — 2026-09-05 — Local MongoDB is a native Windows service, not Docker

**Decision:** Backend connects to the developer's already-installed, already-running native MongoDB Community 8.3 Windows service at `mongodb://localhost:27017`. `infra/docker/docker-compose.yml` is not created/required for Phase 1.

**Why:** `mongosh --eval "db.runCommand({ping:1})"` succeeded against a `Running` `MongoDB` Windows service found at `C:\Program Files\MongoDB\Server\8.3\bin\mongod.exe`. `DEPLOYMENT.md § 1` explicitly states a native local install is an equally valid alternative to docker-compose.

**Impact:** No Docker dependency introduced in Phase 1. `MONGODB_URI` config default is `mongodb://localhost:27017/mentora`. If a docker-compose convenience file is wanted later (e.g. for CI), it can be added without changing application code.

---

### D2 — 2026-09-05 — No system Gradle/Kotlin CLI; vendor the Gradle wrapper

**Decision:** `backend/` will include a committed Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) targeting a Gradle version compatible with JDK 21 (Temurin, confirmed present) and Kotlin's current Ktor-compatible release.

**Why:** `where gradle` / `where kotlin` returned no results on the local machine — only a JDK is globally installed. The wrapper is the standard, reproducible way to build a Kotlin/Ktor project without assuming any global tool install.

**Impact:** All backend build/test commands in this project run via `./gradlew` (bash) or `.\gradlew.bat` (PowerShell), never a bare `gradle`/`kotlin` invocation.

---

### D3 — 2026-09-05 — Codex CLI available; will be used per the roadmap's delegation split

**Decision:** `codex-cli 0.151.0` is installed and available. Codex will be used via the `codex-delegate` skill for scoped, code-heavy Phase 1 module implementation (repositories, DTOs, routine CRUD, tests), per `IMPLEMENTATION_ROADMAP.md`'s Claude/Codex allocation. Claude retains direct authorship/review of: project scaffolding, plugin/config wiring, Auth & RBAC, the demo-checkout no-real-payment boundary, the quiz `isCorrect`-stripping projection, and the AI Tutor interface boundary.

**Why:** Matches the roadmap's explicit "reserve Codex for implementation-heavy, repetitive-code work; Claude for supervision/security-critical review" instruction, now that Codex availability is confirmed rather than assumed.

**Impact:** Expect PHASE_HANDOFF.md to list which Phase 1 modules were Codex-delegated vs. Claude-authored directly.

---

### D4 — 2026-09-05 — Phase 1 builds the `aitutor` module boundary only, no concrete provider call

**Decision:** Per the user's explicit Phase 1 scope instruction, Phase 1 implements the `aitutor` module's persistence (`aiConversations`/`aiMessages`), the `AiProvider` interface (per `AI_TUTOR_ARCHITECTURE.md` §§ 1, 3, ADR-009), the enrollment-gate/lesson-context resolution, and rate-limit wiring — bound to a stub/no-op `AiProvider` implementation (e.g. echoes or returns a fixed placeholder response). The concrete Anthropic Claude API call is deferred to Phase 6, even though the provider (Anthropic) is already architecturally approved (`ADR-009`, 2026-09-04).

**Why:** Directly instructed by the user: "Do NOT implement the concrete Anthropic integration yet unless strictly required for a backend interface boundary... AI Tutor concrete integration belongs to Phase 6," combined with "create backend abstractions/contracts required by future phases where the approved architecture requires it."

**Impact:** Phase 1's `/api/v1/ai-tutor/conversation/messages` endpoint will be functionally real (auth, rate-limit, enrollment gate, persistence, streaming plumbing) but will stream back stub content, not a real LLM completion. Phase 6 replaces only the `AiProvider` implementation binding — no route, schema, or client-contract change expected, consistent with the abstraction's purpose.

---

### D5 — 2026-09-05 — Backend integration tests run against the local native MongoDB instance, not Testcontainers, on this dev machine

**Decision:** `TESTING_STRATEGY.md § 1` specifies Testcontainers (MongoDB module) for backend integration tests. This machine has no Docker installed (`docker` is not on PATH, confirmed during environment audit). Testcontainers requires a container runtime and cannot function without one. Backend integration tests in Phase 1 instead run directly against the already-running local native MongoDB Community 8.3 service (`mongodb://localhost:27017`), using a dedicated `mentora_test` database that is dropped/recreated per test class to keep tests isolated and repeatable.

**Why:** A real, narrow implementation blocker (no container runtime on the local dev machine) — not a disagreement with the locked testing strategy. `DEPLOYMENT.md § 1` already establishes that a native local MongoDB install is an equally valid alternative to a Docker-based one for *running* the stack; this extends the same reasoning to *testing* the stack in the one specific place (Testcontainers) that has a hard Docker dependency. What is tested (real Mongo query/transaction/index behavior, not mocks) is unchanged — only the transport mechanism to reach a real MongoDB instance differs.

**Impact:** Backend test code must not declare a hard Testcontainers runtime requirement that fails outright when Docker is absent. If Docker later becomes available (this machine, or CI), Testcontainers can be reintroduced without changing what the tests assert — flagged here as a reasonable future improvement, not required for Phase 1 to pass its quality gate. This is called out explicitly in the Phase 1 report to the user, since it is a deviation from the locked `TESTING_STRATEGY.md`'s stated tooling (though not from its intent).

---

### D6 — 2026-09-05 — Dependency version corrections found during M1 build verification

**Decision:** Two dependency version pins in `backend/build.gradle.kts` were corrected after Codex's M1 implementation (run 01) could not be compile/test-verified in its own sandbox (no network access there): (1) `org.mindrot:jbcrypt` does not have a `0.10.2` release on Maven Central — corrected to the actual latest, `0.4`. (2) `io.insert-koin:koin-ktor:4.0.0` fails at runtime against Ktor 3.0.1 with `IncompatibleClassChangeError: Found interface io.ktor.server.routing.Routing, but class was expected` — Ktor 3.0 refactored `Routing` from a class into an interface, and Koin 4.0.0's Ktor integration predates that refactor. Corrected to `koin-ktor:4.1.0`, which resolves cleanly and passes the `/healthz` integration test.

**Why:** Both were caught by actually running `./gradlew build`/`test` against the real Maven Central + local MongoDB (Claude has network access; Codex's delegated sandbox did not, so it could only report the code as compile/test-unverified). This is exactly the kind of exact-dependency-API verification the roadmap expects Claude to close the loop on after a Codex-delegated implementation.

**Impact:** `build.gradle.kts` now pins `jbcrypt:0.4` and `koin-ktor:4.1.0` (and its `koin-logger-slf4j` counterpart should be kept in the same `4.1.x` line going forward — do not bump one Koin artifact without the other). Any future dependency-version pin proposed by a delegated task must be verified against Maven Central and an actual `./gradlew build` before being trusted, not assumed correct from training data.

---

### D7 — 2026-09-05 — Refresh tokens are hashed with SHA-256, not BCrypt; passwords stay on BCrypt

**Decision:** `AUTH_SECURITY.md § 4` / `DATABASE_MODEL.md § 2` describe the stored `refreshTokens.tokenHash` as "hashed the same way a password is." Implemented as: **passwords use BCrypt cost 12** (unchanged, low-entropy human-chosen secret, slow hashing is the correct defense), **refresh tokens use SHA-256** (a high-entropy, server-generated 256-bit random opaque value compared on every `/auth/refresh` call).

**Why:** BCrypt exists to slow down brute-forcing a *low-entropy, human-guessable* secret; a random opaque refresh token has no guessable structure, so slow hashing adds only latency (refresh is called often, including via background silent-refresh on every client) without any real security benefit, and BCrypt's 72-byte input truncation is an unnecessary footgun for a token value. Hashing at rest (never plaintext) is the actual locked requirement — the "same way a password is" phrasing conveys that posture, not a literal algorithm mandate. This is a standard, well-established distinction (OWASP's own session-token guidance), not a security downgrade.

**Impact:** `refreshTokens.tokenHash` is a SHA-256 hex digest. Reviewed directly by Claude as part of the Auth & RBAC (M2) security-critical review, per the roadmap's explicit close-supervision requirement for this module.

---

### D8 — 2026-09-05 — `FORBIDDEN_CSRF` added to the error-code taxonomy

**Decision:** Added `ApiException.ForbiddenCsrf` → HTTP 403, code `FORBIDDEN_CSRF`, for a missing/invalid `X-Requested-With: mentora-web` header on a cookie-authenticated state-changing request (`AUTH_SECURITY.md § 10`'s CSRF defense). Documented in `execution/INTEGRATION_CONTRACT.md § 4`.

**Why:** `API_CONTRACT.md § 4`'s taxonomy table lists "example codes" per HTTP-status family, not an exhaustive enum, and CSRF rejection is unambiguously a 403 Forbidden case with no existing code that fits it. Adding a same-family code is narrower and more honest than overloading `FORBIDDEN_ROLE` for an unrelated reason.

**Impact:** Phase 2 (Web) must map `FORBIDDEN_CSRF` to a localized string and must always send `X-Requested-With: mentora-web` on `POST`/`PATCH`/`PUT`/`DELETE` requests to the backend.

---

### D9 — 2026-09-06 — `GET /courses` always forces `status=published`; client-supplied `status` is ignored

**Decision:** The public `GET /api/v1/courses` browse/search endpoint always filters `status: "published"` server-side, regardless of any `status` query parameter a client sends. `API_CONTRACT.md § 7`'s example (`?category=<id>&level=beginner&status=published`) is illustrative of the query-string shape, not a literal instruction to let any caller select `status=draft`.

**Why:** Letting `status` be a client-supplied, unauthorized-checked filter on the public catalog endpoint would let any unauthenticated or cross-account caller enumerate other instructors' unpublished Draft courses — a real data-exposure bug, not a style choice. An Instructor's own Draft courses are surfaced through the separate `instructor/dashboard` aggregation endpoint (its own task, ownership-scoped); Admin's full-status visibility is through `admin/courses` (also its own task). Neither needs `GET /courses` to expose drafts.

**Impact:** `courses` module implementation must hardcode the published-only filter on the public list endpoint and must not accept a client-supplied `status` override there. Documented in `execution/INTEGRATION_CONTRACT.md` § 6 once the courses module lands.

---

### D10 — 2026-09-06 — Course/lesson media reference fields are format-only validated in Phase 1; existence is not checked until the media module exists

**Decision:** `courses.thumbnailMediaId` and a lesson's `videoMediaId` are accepted and stored as opaque ObjectId-shaped strings. Publish-readiness validation (`DATABASE_MODEL.md § 4`'s "title/description/category/price/thumbnail set, ≥1 section with ≥1 lesson with a video") checks only that these fields are **present** (non-null, valid ObjectId format) — not that a `media` document with that id actually exists or is `status: ready`.

**Why:** The `media` module (upload, local filesystem storage) is scheduled after `courses` in this phase's task order, so no real media documents exist yet when `courses` is implemented. Per `BACKEND_ARCHITECTURE.md § 1`'s module-boundary rule, `courses` must not directly query the `media` collection. `DATABASE_MODEL.md`'s literal validation rule only requires the field to be "set," not independently re-verified against another module's data.

**Impact:** A course can technically be published with a `thumbnailMediaId`/`videoMediaId` pointing at nothing (garbage-in-garbage-out) until the `media` module and its own upload flow are what actually populate these fields with real ids in practice. If stronger cross-module referential validation is ever wanted, it belongs in a later, explicit, additive task (e.g. `courses` calling `media`'s service interface, not its collection directly) — not required for Phase 1's quality gate.

---

### D11 — 2026-09-06 — `isEnrolled`/enrollment-awareness on course responses is deferred to the enrollment task

**Decision:** `API_CONTRACT.md § 7`'s description of `GET /courses` as "enrollment-aware when authenticated" is not implemented in the `courses` module itself. The `courses` module ships enrollment-agnostic in this task; an `isEnrolled` (or equivalent) field is added, if at all, when the `enrollment` module is implemented next — as an additive decoration the `enrollment` (or a thin composition layer) contributes, not a change to `courses`' own internals.

**Why:** `enrollment` doesn't exist yet at this point in the task sequence, and `courses` must not depend on it (wrong dependency direction per `BACKEND_ARCHITECTURE.md § 1` — a thin aggregating caller may depend on both, but the base `courses` module shouldn't reach sideways into a not-yet-built sibling). Avoids a circular/forward dependency and matches YAGNI — no caller for the field exists yet.

**Impact:** The `enrollment` task must explicitly revisit `GET /courses/{id}` (and possibly the list) to decide how/whether to surface enrollment status, and update `execution/INTEGRATION_CONTRACT.md` accordingly. Not a Phase 1 gap — a sequencing note for the next task.

---

### D12 — 2026-09-06 — Local MongoDB converted to a single-node replica set to support required transactions

**Decision:** The developer's local MongoDB Community service was standalone (confirmed via `rs.status()` erroring "not running with --replSet"), which cannot run multi-document transactions at all — required by `BACKEND_ARCHITECTURE.md § 4` for demo-checkout completion, lesson-completion→progress→certificate, and quiz-submission→progress→certificate. Rather than deviate from the locked transactional design, the user converted the local MongoDB service to a single-node replica set (`rs0`): added `replication.replSetName: rs0` to `mongod.cfg`, restarted the service, ran `rs.initiate()` once. Verified afterward: `rs.status()` reports `PRIMARY`/healthy, and a real cross-collection transaction (insert in collection A + insert in collection B, single commit) succeeds end-to-end.

**Why:** A single-node replica set is the standard, low-risk, fully-reversible way to get real MongoDB ACID transactions on a local dev machine without Docker/Testcontainers — CRUD behavior for any other local project sharing this MongoDB install is unaffected; only transaction support and oplog maintenance are added. This was presented to the user as a choice (vs. dropping transactions and relying on the unique-index idempotency guarantee alone) — the user chose to convert to a replica set, preserving the locked architecture's transactional guarantees as originally specified rather than deviating from them.

**Impact:** `config/AppConfig.kt`'s default `MONGODB_URI` is now `mongodb://localhost:27017/mentora?replicaSet=rs0` (explicit, though the driver would auto-detect replica-set membership from a bare seed host too). The `enrollment`, `progress`, `quiz`, and `certificates` modules (all still upcoming) can now use real `ClientSession`-based transactions exactly as `BACKEND_ARCHITECTURE.md § 4` specifies, with no workaround needed. `DEPLOYMENT.md`'s local-setup instructions (Phase 1's own local run instructions, still to be written) must mention this one-time replica-set conversion step for a fresh machine.

---

### D13 — 2026-09-06 — `progress` document creation is lazy, not literally inside the checkout transaction

**Decision:** `DATABASE_MODEL.md § 6` says a student's `progress` document is "created (all fields zeroed/empty) at the same moment as its Enrollment, inside the same transaction." `BACKEND_ARCHITECTURE.md § 4`'s own transaction-boundary table, however, lists the demo-checkout transaction as touching only `demoPurchases` + `enrollments` — `progress` isn't in that row. Since the `progress` module is built in a later task than `enrollment` (this phase does backend-first, module-by-module, not the original roadmap's per-feature-across-all-clients ordering), `enrollment` implements only its own two collections; `progress` documents are created lazily — on the student's first progress read or first lesson-completion for that course, whichever comes first — rather than literally inside the checkout-completion transaction.

**Why:** Per `BACKEND_ARCHITECTURE.md § 1`'s module-boundary rule, `enrollment` must not reach into `progress`'s collection directly, and `progress` doesn't exist yet at this point in the task sequence to expose a service interface for `enrollment` to call. Lazy creation preserves every guarantee that actually matters at the API/data level (unique `(userId, courseId)` progress document, zeroed initial state, one-per-enrollment) — the only difference is the exact instant the row is written, which is invisible to any client contract (`GET .../progress` synthesizes a zero-state response, or creates the row on first read/write — the `progress` task's own implementation detail to pick).

**Impact:** The `progress` module's own task brief must explicitly implement lazy progress-document creation (find-or-create semantics on first relevant call) rather than assuming an already-existing row from checkout. `execution/INTEGRATION_CONTRACT.md` will note this once `progress` lands.

---

### D14 — 2026-09-06 — Known limitation: checkout-completion idempotency handles sequential retries, not true simultaneous concurrency

**Decision (documented, not changed):** `enrollment/service/EnrollmentService.kt`'s `complete()` catches `MongoWriteException` with `ErrorCategory.DUPLICATE_KEY` to turn a race on the `(userId, courseId)` unique index into a graceful idempotent `200` response. Under a true multi-document-transaction write conflict (two requests' transactions racing on the same insert at the storage-engine level), MongoDB can instead surface a `TransientTransactionError`-labeled exception at commit time rather than a clean duplicate-key error — that path is not explicitly caught here and would currently surface as a `500 INTERNAL_ERROR`.

**Why left as-is:** `API_CONTRACT.md § 6`'s own stated motivation for checkout idempotency is "a retried request (e.g. a flaky network causing a client-side resend)" — a **sequential** retry, which this implementation handles correctly and is what's tested. A true simultaneous double-submit (two exactly-concurrent requests from the same student) is a much rarer edge case for a single-user-driven demo checkout button, and adding transient-error-labeled retry handling for it now would be speculative hardening beyond what the locked spec asks for at MVP scale.

**Impact:** Noted here as a known, minor, non-blocking limitation for the Phase 1 report. If real concurrent-submission bugs are ever observed (unlikely at this product's scale), the fix is additive: catch `MongoException` where `hasErrorLabel("TransientTransactionError")` and retry the transaction once before giving up.

---

### D15 — 2026-09-06 — Course-completion crossing + certificate issuance is deferred to the combined Quiz+Certificates task, not built in the `progress` task

**Decision:** `progress` (this task) implements per-lesson completion tracking, playback-position heartbeat, and a lessons-only `completionPercent`, enrollment-gated. It does **not** implement `courseCompletedAt`-setting or certificate issuance — `courseCompletedAt` stays `null` always in this task. The next task ("Quiz + Certificates") adds course-completion-crossing detection to *both* the lesson-completion path (course with no quiz: all-lessons-done completes it) and the quiz-submission path (course with a quiz: all-lessons-done + quiz-passed completes it), plus the transactional certificate insert, in one combined piece of work.

**Why:** `DATABASE_MODEL.md § 6`'s `quizPassed` field and `PRODUCT_SPEC.md`/`USER_FLOWS.md § 17`'s two completion paths ("with or without a quiz") mean "did this course just complete?" is inherently a joint fact about `progress` + whether a `quizzes` document exists for the course — information `progress` alone doesn't have yet (the `quiz` module doesn't exist at this point in the task sequence). Rather than force an awkward partial implementation now and a second pass later that touches the same lesson-completion code path twice, both directions of the crossing logic (lesson-complete → maybe-complete, quiz-pass → maybe-complete) are implemented together with `quiz` and `certificates` in the next task, matching `BACKEND_ARCHITECTURE.md § 4`'s transaction table, which already couples these three collections at exactly these two trigger points.

**Impact:** The next task's brief explicitly includes modifying `progress`'s lesson-completion service method (an authorized, anticipated cross-module addition, not an ad hoc one) to add the completion-crossing + certificate-transaction logic once `quiz`'s existence-check is available to call into.

---

### D16 — 2026-09-06 — Quiz submission updates `progress.quizPassed` but does not (yet) issue certificates

**Decision:** The `quiz` task implements quiz authoring/editing, student-facing fetch (`isCorrect`-stripped), and attempt submission — grading server-side, inserting a `quizAttempts` record, and updating `progress.quizPassed` to the boolean result of the **latest** attempt (so a fail after a prior pass, or a pass after a prior fail, always reflects the most recent attempt — not "sticky true"), all inside one real MongoDB transaction. It does **not** yet check "did this submission just complete the course" or insert a certificate — `BACKEND_ARCHITECTURE.md § 4`'s quiz-submission transaction row also includes `certificates`, added in the following task alongside the `certificates` module itself and the matching addition to `progress`'s lesson-completion path (same reasoning as D15 — this is genuinely one coupled unit of work, split into two reviewable diffs instead of one large one for the two roadmap-flagged close-review items, `isCorrect`-stripping here and certificate-issuance correctness next, to stay cleanly separable).

**Why:** Splitting the tightly-coupled progress+quiz+certificates completion logic into two sequential tasks (quiz mechanics alone, then completion-crossing+certificates tying all three together) keeps each diff reviewable on its own terms — this task's diff can be reviewed purely for grading/`isCorrect`-stripping correctness without the added surface area of transaction-spanning completion logic.

**Impact:** After this task, a student can take and retake a quiz and see accurate pass/fail history, but no course ever shows `courseCompletedAt` set via the quiz path until the next task lands (the lesson-only completion path is *also* still not wired for courses that have a quiz, by the same token — `progress.complete()` isn't modified in this task either). Not a regression — Phase 1's quality gate is evaluated after the next task completes this pair.

---

### D17 — 2026-09-06 — Certificate issuance is its own atomic step immediately after (not literally inside) the triggering write, to avoid a circular module dependency

**Decision:** `certificates` depends one-way on `progress`, `quiz`, `courses`, and `users` (to build the denormalized snapshot and decide completion eligibility). `progress` and `quiz` do **not** depend back on `certificates`. The completion-crossing check runs as a **second, immediately-following transaction** — triggered from the route layer right after `progress.complete()` or `quiz.submit()`'s own transaction commits — rather than literally inside that same transaction. That second transaction still atomically pairs exactly what matters: setting `progress.courseCompletedAt` and inserting the `certificates` document happen together, in one transaction owned by `certificates`, so a certificate can never exist without the matching completion state (or vice versa).

**Why:** `progress` already depends on `quiz` is false — actually `quiz` depends on `progress` (for `setQuizPassed`). Detecting "did a lesson-completion just finish the course" requires knowing whether the course has a quiz, i.e. `progress` would need to depend on `quiz` too — creating a real circular dependency (`quiz → progress → quiz`) that Koin/constructor injection cannot resolve. Routing the completion-check-and-certificate-issuance through a new one-way-dependent `certificates` module, triggered from the route layer rather than chained service-to-service, is the standard way to break this cycle without weakening the actual data-integrity guarantee: `BACKEND_ARCHITECTURE.md § 4`'s real intent — a certificate is never orphaned from, or missing given, the completion state it represents — is fully preserved; only the literal "single transaction spanning three collections from one write's callsite" mechanic is split into two adjacent transactions.

**Impact:** A theoretical crash in the narrow window between the two transactions could leave a course fully eligible but not yet certified. Mitigated by making the completion-check idempotent and re-triggerable — `GET /courses/{id}/progress` (already lazy-creating, per D13) also re-runs the same eligibility check on every read as a self-healing safety net, so the gap self-corrects on the student's very next progress view. This is judged acceptable for a local single-instance MVP portfolio demo, not a distributed-systems-scale concern.

---

### D18 — 2026-09-06 — Codex usage limit hit mid-Phase-1; user chose to wait rather than switch allocation

**Decision:** During the Learning Paths module task (run 08), Codex returned "You've hit your usage limit... try again at 4:57 AM" (an account-level quota, not a task failure — confirmed via the run's raw event log, not a code/task defect). Presented the user a choice: wait for the quota to reset and resume the established Codex-implements/Claude-reviews workflow, or have Claude implement the remaining Phase 1 modules directly. **User chose to wait.**

**Why this matters to record:** Explains a visible gap/pause in the implementation timeline and confirms the Claude/Codex allocation for the rest of Phase 1 remains unchanged (not a scope or process deviation) — purely a scheduling pause caused by external account quota, resolved by waiting.

**Impact:** Learning Paths (task 14) has partial, uncommitted work in the working tree from the interrupted run (all layers scaffolded, no tests yet) — preserved, not discarded, to resume from once Codex is available again.

---

### D19 — 2026-09-06 — Session paused for machine shutdown mid-Learning-Paths-task

**Decision:** Codex's retry of the Learning Paths task (run 08c, resuming session `01a073f2-bf8d-7a21-b0e0-081fd139e0a4`) was making progress (had produced a test file in addition to the module scaffold) when the user requested an immediate machine shutdown. The in-flight dispatch was stopped via `TaskStop` (a clean termination, not a crash) rather than left running unattended through a shutdown. No build/test verification, review, or commit was performed on this partial work — it is left exactly as Codex produced it, uncommitted, for the next session to pick up.

**Why:** User-directed safe-shutdown request takes priority over completing the in-flight delegation; stopping the task cleanly (rather than letting a shutdown kill it uncontrolled) and leaving the working tree untouched otherwise is the safest way to honor both "preserve partial work" and "stop background tasks" simultaneously.

**Impact:** See `execution/CURRENT_STATUS.md`'s "EXACT RESUME POINT" section for the full resume procedure. No git operations (commit/reset/discard) were performed as part of this pause.

---

### D20 — 2026-09-06 — Learning Paths (task 14) resumed and closed out: 3 test-only bugs found and fixed, application code verified correct

**Decision:** Resumed from the machine-shutdown pause. Built and ran the partial Learning Paths work as-is first (per the documented resume procedure) rather than assuming it was broken or re-dispatching Codex. All 4 `LearningPathsIntegrationTest` cases initially failed. Root-caused and fixed three bugs, all confined to the test file — the production module code (`LearningPathsModule.kt`, `repository/`, `service/`, `routes/`) required zero changes and was verified correct as Codex left it:

1. `postJson`/`register`/`login` test helpers had two conflicting 2-arg overloads — `postJson(path, token)` and the intended-but-effectively-shadowed `postJson(path, body)` — both `(String, String)`, so `register()`'s call resolved to the token overload, silently sending an empty `{}` body to `/api/v1/auth/register` instead of the real payload. Fixed by adopting the exact helper signature already used (and working) in `CertificatesIntegrationTest.kt`/`CoursesCategoriesIntegrationTest.kt`: a single `postJson(path, body, token: String? = null)`, with call sites updated accordingly.
2. `seedPath()`'s direct-to-MongoDB test fixture inserted `createdAt` as a raw `java.util.Date`, producing a native BSON `DATE_TIME`. But `LearningPathDocument.createdAt: Instant` (no `@Contextual` override, same as every other module's `Instant` fields) is written by the app's own Kotlinx-serialization Mongo codec as an ISO-8601 **string**, so real documents and this raw-inserted fixture were BSON-type-inconsistent — decoding threw `BsonInvalidOperationException`. Fixed by seeding `createdAt` as `java.time.Instant.now().toString()` instead.
3. One assertion checked `body["progressPercent"]?.jsonPrimitive?.content == "null"` to assert a null field for a guest request. The app's global JSON config sets `explicitNulls = false` (`plugins/Serialization.kt`), so null fields are omitted from the response entirely rather than serialized as JSON `null` — the established, already-used pattern elsewhere (`CertificatesIntegrationTest`, `ProgressIntegrationTest`) is `assertFalse(body.containsKey(...))`. Fixed to match.

**Why this matters to record:** None of these were application-logic defects — they were test-harness mistakes introduced while authoring a brand-new test file under Codex's interrupted/retried session (D18/D19), not present in any previously-reviewed module. Recording this distinction so the module's certificate-of-review is accurate: the learning-paths production code was correct on first read: order-preserving course resolution, dangling-course omission from both detail and the progress denominator, idempotent follow (unique compound index + `setOnInsert` upsert) and unfollow (plain idempotent delete), and guest-safe optional-auth on the detail route all verified via the now-passing tests plus direct code reading.

**Impact:** Full `./gradlew build` (all 8 integration test classes, 25 tests) is green. Task 14 committed. Proceeding to task 15 (Media module).

---

### D21 — 2026-09-06 — Media documents carry an additive `courseId` field to resolve lesson-video ownership/enrollment without a new reverse lookup

**Decision:** `DATABASE_MODEL.md § 15`'s `media` fields (`ownerRefId` interpreted per `kind`) don't by themselves let the `media` module answer "which course does this `lessonVideo`'s `ownerRefId` (a `lessonId`) belong to?" — needed both to check upload-time ownership (is this Instructor the owning course's Instructor?) and playback-time enrollment (is this Student enrolled in the owning course?). Rather than add a new lesson→course reverse-lookup method to `CourseService` (or have `media` reach into `courses`' collection directly, which `BACKEND_ARCHITECTURE.md § 1` forbids), the `media` document gains one additive field, `courseId: ObjectId?` — populated only for `kind: lessonVideo`, supplied by the client at upload time as an extra multipart field, `null` for `courseThumbnail` (where `ownerRefId` already *is* the courseId) and `avatar` (no owning course). At upload time, `media` calls the already-exposed `CourseService.requireOwnership(courseId, principal)` (added in the commit just before this task specifically for this kind of cross-module reuse) and additionally verifies the given `lessonId` actually appears in that course's `sections[].lessons[]` (defense-in-depth against a client pairing an unrelated `lessonId` with a `courseId` it doesn't belong to). At playback time, `media` calls the existing public `CourseService.get(courseId, principal)` to read the owning instructor, and `EnrollmentService.requireEnrollment(userId, courseId)` for the enrollment gate.

**Why:** Keeps the module-boundary rule intact (no direct cross-collection queries) by reusing methods already public on `CourseService`/`EnrollmentService`, rather than inventing a new cross-module query surface or letting `media` query `courses`' collection directly. A purely derived/recomputed reverse lookup was the only other option and would have required exactly the kind of new service method this avoids.

**Impact:** `POST /api/v1/media/uploads`'s multipart fields are `{ kind, ownerRefId, contentType, courseId? }` — `courseId` is required and validated when `kind == lessonVideo`, ignored otherwise. This is a non-breaking, additive extension of `MEDIA_ARCHITECTURE.md § 3`'s described shape (that section doesn't enumerate the exhaustive field list). `execution/INTEGRATION_CONTRACT.md § 7` will record the as-built shape once this task lands.

---

### D22 — 2026-09-06 — Media upload size limits (not pinned in any locked doc) set for local-demo scale

**Decision:** `AUTH_SECURITY.md § 8` locks the content-type allowlist (`video/mp4`, `video/webm`, `image/jpeg`, `image/png`, `image/webp`) but no document states a max upload size. Set: images (`courseThumbnail`/`avatar`) max **5 MB**; videos (`lessonVideo`) max **500 MB**. Enforced server-side before any disk write (streaming size check as multipart bytes arrive, not after buffering the whole file), per `ADR-008`'s "twice, client and server, server authoritative" rule — Phase 1 only implements the server-side half; client-side pre-validation is a later Phase 2 UX concern. Content-type is also cross-checked against `kind` (`courseThumbnail`/`avatar` → must be one of the three image MIME types; `lessonVideo` → must be one of the two video MIME types) — a client can't upload a video as a "courseThumbnail" or vice versa.

**Why:** A concrete number is required to implement a working upload endpoint; 5 MB/500 MB are conservative, generous-enough-for-a-demo bounds for a single local developer machine, not a product requirement derived from any locked doc.

**Impact:** If real course videos ever need to exceed 500 MB, this is a one-line config-constant change, not a design change. Not treated as a locked architectural value — safe to revisit.

---

### D23 — 2026-09-06 — Signed playback reference implemented as a short-lived, purpose-scoped JWT, verified outside the main `jwt-auth` Authentication provider

**Decision:** `MEDIA_ARCHITECTURE.md § 5`'s "short-lived signed reference" for lesson-video playback is implemented as a second JWT (reusing the already-present `com.auth0:java-jwt` dependency and `appConfig.jwtSigningSecret`), distinct from the access token: claims `mediaId` and `purpose: "media-playback"`, 5-minute expiry, verified by hand (`JWT.require(...).build().verify(token)`) inside the streaming route handler itself — not registered as a second Ktor `Authentication` provider, since it's a resource-scoped capability token bound to one `mediaId`, not a user-session credential. Route shape: `GET /api/v1/media/{mediaId}/playback-url` (normal `jwt-auth`, enrollment-checked per D21) returns `{ url, expiresAt }` where `url` is `/api/v1/media/{mediaId}/stream?token=<jwt>`; `GET /api/v1/media/{mediaId}/stream` is a public route (no cookie/bearer auth — a native `<video>`/`ExoPlayer`/`AVPlayer` element can't easily attach custom headers) that verifies the query-param token's signature, expiry, and that its `mediaId` claim matches the path parameter before streaming any bytes, per `ADR-008`'s "an unauthorized or expired request is rejected before any bytes are streamed."

**Why:** Reuses an already-vetted signing mechanism/secret instead of inventing a new one; keeps the token purpose-scoped (a stolen playback token can only stream that one video for 5 minutes, never anything else, and isn't a valid access token or vice versa) via the `purpose` claim.

**Impact:** Video streaming (`.../stream`) uses `LocalFileContent` + the already-installed `ktor-server-partial-content` plugin (`install(PartialContent)`, not yet installed in `plugins/Security.kt` or elsewhere — this task installs it) so HTTP range requests (scrubbing/seeking) work for free without hand-rolled range-header parsing.

---

### D24 — 2026-09-06 — Media documents are only ever inserted with `status: ready`; the `pendingUpload`/`failed` lifecycle states are unused in Phase 1

**Decision:** `DATABASE_MODEL.md § 15`'s lifecycle (`pendingUpload` → `ready`/`failed`) describes a two-phase presigned-upload flow. `MEDIA_ARCHITECTURE.md § 3` (the more specific, local-scope-amended doc) instead describes one synchronous backend-mediated request that writes bytes and inserts the `media` document together — "if step 3 fails part-way..., no `media` document is created... there is no orphaned-record cleanup problem." Phase 1 follows `MEDIA_ARCHITECTURE.md`'s simpler shape literally: a `media` document is inserted (with `status: "ready"`) only after `MediaStorage.store(...)` has already succeeded; any failure before that point returns an error to the client and writes nothing. The `status` field is kept on the document (for schema-compatibility with `DATABASE_MODEL.md` and any future async-processing evolution) but is always `"ready"` in Phase 1 — `pendingUpload`/`failed` are dead enum values for now.

**Why:** Matches the already-locked, more-specific `MEDIA_ARCHITECTURE.md § 3` flow exactly; inventing an unused async two-phase state machine now would be speculative (YAGNI) — no caller ever produces a `pendingUpload` document to transition out of.

**Impact:** `execution/INTEGRATION_CONTRACT.md § 7` will note this once the module lands. A future async/cloud-storage migration (`ADR-008`'s optional Migration Path) is exactly where `pendingUpload`/`failed` would become live states again — not required now.

---

### D25 — 2026-09-06 — `media.ownerRefId` is stored as a `String`, not an `ObjectId`, because lesson ids are UUID strings

**Decision (correction to D21's assumed type):** D21 assumed `ownerRefId` would be an `ObjectId` for every `kind`. During implementation this proved wrong for `kind: lessonVideo`: `courses/service/CourseService.kt` generates a lesson's `lessonId` as `UUID.randomUUID().toString()` (a stable string identifier, deliberately not a Mongo `ObjectId`, per `DATABASE_MODEL.md § 4`'s own note that section/lesson ids are "a stable string/UUID, not a Mongo ObjectId, since sections are addressed by clients for reorder operations"). `MediaDocument.ownerRefId` is therefore typed `String`: validated as ObjectId-hex for `courseThumbnail`/`avatar` (where `ownerRefId` really is a courseId/userId), and validated as a well-formed UUID matching a real lesson id on the owning course for `lessonVideo`.

**Why:** Requiring `ObjectId` format for `lessonVideo.ownerRefId` would reject every real lesson id a course actually produces — not a hypothetical edge case, a guaranteed failure for the module's main use case. Caught and fixed during implementation rather than shipped broken.

**Impact:** No API-visible change — `ownerRefId` was always an opaque string on the wire (multipart form field), this only affects the Kotlin type stored server-side. `courseId` (the D21 additive field) stays a real `ObjectId`, since course ids are genuinely Mongo `ObjectId`s.

---

### D26 — 2026-09-06 — Media upload multipart request requires form fields before the file part

**Decision:** `POST /api/v1/media/uploads`'s handler reads `kind`/`ownerRefId`/`contentType`/`courseId`/`durationSeconds` from multipart form fields into memory as they stream past, and processes the file part (calls the upload service, which needs those fields already resolved) at the moment the file part itself arrives. This means the client **must** send the form fields before the file part in the multipart body — the same requirement direct-to-object-storage POST uploads (e.g. S3 presigned POST) already impose, and satisfied automatically by every standard multipart-builder (browser `FormData`, `curl -F`, Ktor's own `formData { }`) as long as the caller appends the metadata fields first, which is also the natural order to write the code in.

**Why:** Ktor's multipart parsing is a single forward-only stream — there is no "read all parts, then decide" step without buffering the entire (up to 500 MB) file part in memory first, which the size-limit design explicitly avoids (D22's streaming-with-abort approach). Requiring metadata-before-file is the standard, low-cost way to avoid that buffering.

**Impact:** **Phase 2 (Web)'s upload UI must append `kind`, `ownerRefId`, `contentType`, and (for lesson videos) `courseId`/`durationSeconds` to the `FormData` before appending the file blob.** Recorded here and in `execution/INTEGRATION_CONTRACT.md § 7` so this isn't rediscovered the hard way during Phase 2.

---

### D27 — 2026-09-06 — Instructor aggregation endpoint (task 16): Codex's run was cut off by an OpenAI usage-limit error before self-verification; Claude ran the gates independently and fixed one test-only bug

**Decision:** Codex (dispatched via the established per-module brief pattern) wrote the `instructor` module (`repository`/`service`/`routes`/Koin wiring, per `architecture/BACKEND_ARCHITECTURE.md § 3`'s explicit design for this module: no collection of its own, reads directly across `courses`/`enrollments`/`progress`) plus `InstructorIntegrationTest.kt`. Its dispatch then failed with a hard `"You've hit your usage limit"` error from the OpenAI API — this happened right after Codex hit and worked around a local Gradle issue (`gradlew.bat`'s default `GRADLE_USER_HOME` resolved to an unwritable `C:\.gradle`; Codex's fix was to point `GRADLE_USER_HOME` at the repo's own `.gradle` directory instead), but before it ever got to actually run `gradlew.bat test`. Rather than wait idle for the quota reset (reported as available again Sep 7, 05:36) or re-dispatch, the orchestrating Claude session reviewed the diff directly and ran the verification gates itself, reusing the same `GRADLE_USER_HOME` workaround Codex had already found. `gradlew.bat test --rerun-tasks` surfaced exactly one failure: `InstructorIntegrationTest`'s own test-fixture helper `tokenUserId()` decoded the JWT payload's `sub` claim, but this codebase's `auth/service/TokenIssuer.kt` names that claim `userId`, not `sub` — a one-line test-fixture mistake; the production `instructor` module code required zero changes. Fixed directly, re-ran (31 tests / 10 suites, all green), then `gradlew.bat build` (green).

**Why:** A `codex-delegate` dispatch failing mid-run for a provider-side quota reason (not a code defect) doesn't invalidate the work already produced on disk — reviewing it and re-verifying independently, which this workflow already requires regardless of a clean Codex exit, is faster and safer than an idle multi-hour wait for quota reset. Recording the specific test-only bug for the same reason D20 recorded its three test-only bugs: to keep the record accurate that the production `instructor` module was correct as Codex wrote it, and the one defect found was confined to a test fixture, not application logic.

**Impact:** Task 16 committed. `GRADLE_USER_HOME=<repo>/.gradle` is a locally-discovered environment workaround for this machine's default (unwritable) Gradle user home — not a project convention change, but worth knowing for any future local gate run on this machine, Codex-dispatched or not.
