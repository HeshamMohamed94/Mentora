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

---

### D28 — 2026-09-06 — Admin aggregation endpoints (task 17): "users" and "instructors" are disjoint role-scoped lists, not "all accounts"; `admin` reads `enrollments` directly too, beyond the three collections BACKEND_ARCHITECTURE.md's module table names

**Decision:** Implemented the four `architecture/API_CONTRACT.md` Admin routes (`GET /admin/dashboard`, `/admin/courses`, `/admin/users`, `/admin/instructors`) as a new `admin` module, following the exact same no-own-collection, direct-multi-collection-read pattern already established for `instructor` (D-precedent from task 16). Two things this milestone might have sounded like it needed were confirmed already done and left untouched: category CRUD (`categories` module, task 9) and the Admin-usable course "unpublish" moderation action (`POST /courses/{id}/unpublish` already accepts `Role.admin`, task 5's Courses module). Two scope clarifications made explicit before dispatch, per `product/USER_ROLES.md`'s Admin capabilities ("Manage users (list students)" / "Manage instructors (list instructors)" as two separate bullets) and `ux/INSTRUCTOR_ADMIN_UX.md § 2`'s dashboard cards ("total students" / "total instructors" named separately): (1) `/admin/users` and `/admin/instructors` are disjoint, role-filtered lists (`role == student` and `role == instructor` respectively) — there is no "all accounts" list anywhere in this module; (2) `architecture/BACKEND_ARCHITECTURE.md § 3`'s module table names only `courses`, `users`, `categories` as what `admin` reads, but the UX contract's required `enrollmentCount` columns (on both the Courses and Users tables) need `enrollments` — read directly, the same category of documented cross-module-read exception already established for `instructor` (D-precedent), not a new one. `categories` itself is not read by this module at all — nothing in the four endpoints needs category data.

**Why:** Both role-scoping and the extra `enrollments` read follow directly from the UX/product docs' literal wording and from precedent already set for the sibling `instructor` module, so recording them here (rather than as a locked-doc contradiction) is exactly what `DECISIONS_LOG.md`'s stated purpose covers — filling in a detail the architecture docs left implicit, not overriding one.

**Impact:** `execution/INTEGRATION_CONTRACT.md § 6` records the as-built shape. `admin/users`'s and `admin/instructors`' shared `q` search (matches `name` OR `email`, case-insensitive) escapes the untrusted query via `Pattern.quote` before building the Mongo regex filter — worth knowing if a future task ever adds another free-text admin filter, so the same escaping isn't forgotten.

### D29 — 2026-09-06 — Task 17's Codex dispatch hit the same OpenAI usage-limit wall twice before succeeding on a third attempt

**Decision:** The first two `codex-delegate` dispatches for task 17 both failed immediately with the same `"You've hit your usage limit... try again at Sep 7th, 2026 5:36 AM"` error already seen once during task 16 (D27) — the first attempt after Codex had already produced task 16's work, the second before Codex wrote anything at all for task 17 (`touched files: 0`). Per the user's explicit choice when asked, a third dispatch was attempted immediately rather than waiting for the reported reset time, and it succeeded cleanly: Codex wrote the full `admin` module, ran the build/test gates itself, and self-applied `clean-code-guard`/`test-guard` (one self-fix: an unfiltered-course-list branch that would have built an invalid empty MongoDB `$and`). Claude still independently re-ran `gradlew.bat test --rerun-tasks` (fresh, non-cached, live MongoDB, 35 tests/11 suites) and `gradlew.bat build`, and read the full diff, rather than trusting the self-report alone.

**Why:** The usage-limit error is intermittent/provider-side (it did not, in fact, hold for the reported multi-hour window) — worth recording so a future session doesn't treat a single such failure as a hard multi-hour block; one immediate retry is a reasonable first response before falling back to waiting or an alternate implementer.

**Impact:** No code or contract impact. Purely a process note for future Codex dispatches on this machine/account.

---

### D30 — 2026-09-06 — AI Tutor scaffold (task 18): request pairs `courseId` with `lessonContextId` (no reverse lookup exists), Phase 1's system prompt omits live enrollment data, streaming is plain chunked text (no SSE), and both rate-limit caps are now configuration-driven

**Decision:** Implemented the `aitutor` module strictly to `DECISIONS_LOG.md` D4's already-recorded Phase 1 boundary (persistence, the `AiProvider` interface, enrollment-gate/lesson-context resolution, rate-limit wiring, bound to a stub provider — no real Anthropic call). Four implementation-time specifics resolved before dispatch, all recorded here since none is pinned in a locked doc at this level of detail:
1. **`courseId` + `lessonContextId` are a paired request field**, not `lessonContextId` alone as `AI_TUTOR_ARCHITECTURE.md § 1`'s prose might suggest — lesson ids are UUID strings scoped inside a course's embedded curriculum (D25) with no reverse lookup from lesson id back to owning course anywhere in this codebase. Exactly the same problem, and exactly the same fix, as the `media` module's `lessonVideo` upload (D21): the client (which is always viewing a specific course when it has a `lessonContextId` to send) supplies both. `POST /ai-tutor/conversation/messages`'s body is `{ content, courseId?, lessonContextId? }`, `400 VALIDATION_ERROR` if exactly one of the pair is present without the other.
2. **Phase 1's system prompt is a fixed persona/constraints string only** — no live enrolled-course-list injection for the "global mode" case `AI_TUTOR_ARCHITECTURE.md § 3` describes. D4 doesn't list that specific piece as a Phase 1 deliverable, and building it would mean a new read aggregation (enrollment list → batch course-title resolution) purely to feed a stub provider that doesn't act on it — deferred to whichever phase implements the real Anthropic call.
3. **Streaming wire format:** plain incrementally-flushed `text/plain` chunks via Ktor's `respondTextWriter` (genuinely chunked over the wire — the stub's tokens are flushed as they're produced, not buffered into one response), not Server-Sent Events — no `ktor-server-sse` dependency exists in this project and adding one wasn't judged worth it for a Phase 1 stub; `AI_TUTOR_ARCHITECTURE.md § 1` step 8 explicitly allows either ("chunked response / SSE-equivalent"). No client/UI exists yet to have a contrary opinion.
4. **Rate limiting now has both caps `AI_TUTOR_ARCHITECTURE.md § 6` requires** ("a message-per-minute cap and a daily message cap, both tunable via configuration") — previously only a hardcoded per-minute limiter existed (added speculatively during the M1 plugin scaffold). Added `AppConfig.aiTutorMessagesPerMinute`/`aiTutorMessagesPerDay` (env-driven, defaulting to the prior 20/minute plus a new 200/day), `configureRateLimiting` now takes `AppConfig`, and a second `RateLimitName("aiTutorDaily")` registration is nested alongside the existing per-minute one on the messages route — verified independently (both caps trigger `429` on their own, at different thresholds).

**Why:** Each of the four fills a genuine gap between the architecture prose and what the existing codebase can actually do (or what a stub provider actually needs) — none contradicts a locked decision, so recording here rather than reopening any ADR.

**Impact:** `execution/INTEGRATION_CONTRACT.md § 8` records the as-built request/response shape and the streaming format. Phase 6 (the real Anthropic integration) is expected to: (a) keep the `courseId`+`lessonContextId` pairing (client contract doesn't change), (b) likely add the enrolled-course-list system-prompt content point 2 defers, (c) can keep the same streaming transport or upgrade it — client contract stays chunked-text either way per D4, (d) bind a real `AiProvider` implementation via Koin only, no route/persistence change.

### D31 — 2026-09-06 — Independent gate re-verification for task 18 surfaced a pre-existing flaky test in `MediaIntegrationTest` (task 15), unrelated to `aitutor`; fixed separately

**Decision:** While independently re-running `gradlew.bat test --rerun-tasks` for task 18 (per the established review discipline — never trust Codex's self-report alone), `MediaIntegrationTest`'s signed-playback-token tamper assertion failed twice in a row, then passed several times after. Root cause: the test flipped the *last* character of a JWT to assert signature-tamper detection, but base64url's final character in a length ≡ 3 (mod 4) segment carries 2 unused/padding bits — depending on the specific token generated that run (embeds a fresh `mediaId` and timestamp), flipping that exact character sometimes decodes to byte-identical signature bytes, so the "tampered" token verifies successfully and the test flakes to a false pass (asserted `401`, got `200`). Fixed by flipping the *first* character of the signature segment (right after the JWT's last `.`) instead — always meaningful, non-padding bits — verified 8/8 clean runs after the fix, then a full fresh suite run (41 tests, 12 suites, 0 failures) and `gradlew.bat build` green.

**Why:** This file wasn't touched by task 18's `aitutor` work at all — a pre-existing bug from task 15, unmasked only by the specific random token bytes generated on a couple of unlucky runs. Recording it (and fixing it as its own separate commit, not folded into task 18's) keeps the two changes' histories legible, and closes a real gap before it causes a flaky-CI surprise during task 22's Phase 1 quality-gate pass.

**Impact:** No production-code or contract change — test-only. Commit `82e1664`, landed immediately before task 18's own commit.

---

### D32 — 2026-09-06 — Backend test suite completeness review (task 19): added the previously-nonexistent unit-test layer plus four missing integration routes; zero production bugs found

**Decision:** An audit (per `architecture/TESTING_STRATEGY.md § 1`'s three-tier backend testing requirement — unit/integration/API-contract) found two concrete gaps, both now closed: (1) zero unit tests existed anywhere in the backend despite the strategy doc naming three explicit business-logic examples that should be unit-tested with MockK — added `QuizServiceTest.kt` (scoring/grading: 0%, 100%, the 70%-inclusive pass boundary, a nonexistent-question answer, a missing answer), `CourseServiceTest.kt` (every `publish()` validation branch plus the success path), `ProgressServiceTest.kt` (zero-lesson guard, idempotent re-completion, 100% on the last lesson, integer-truncation on partial completion — e.g. 1/3 → 33, not 33.33/34); (2) `POST /courses/{id}/unpublish` had zero test coverage anywhere despite being named in both M11's and M12's roadmap acceptance criteria — added coverage for owner-unpublish, Admin-unpublish-any-course, non-owner/wrong-role rejection, and the specific roadmap-named behavior that an already-enrolled student keeps player/quiz access after their course is unpublished. Also closed three smaller never-exercised routes in the same module: `PATCH .../sections/{sectionId}` (rename), `DELETE .../sections/{sectionId}` and `DELETE .../sections/{sectionId}/lessons/{lessonId}` (both asserting order re-compaction after deletion, not just "the count went down"). The one unit-test judgment call: `QuizService.submit()` opens a real `MongoClient.startSession()`/transaction internally — the test mocks the client/session (`hasActiveTransaction() = true`, relaxed session ops) rather than trying to fake a real transactional Mongo interaction, since the transaction *mechanics* are already covered by the existing live-MongoDB integration suite (`CertificatesIntegrationTest.kt`) — this unit test's job is only the pure scoring math.

**Why:** Matches `TESTING_STRATEGY.md § 1`'s explicit requirement (unit tests for the three named examples) and closes a real, previously-undetected blind spot (`unpublish` — a locked-doc-named acceptance criterion for two milestones — had literally never been called by any test before this).

**Impact:** No production code changed — every test added asserts already-correct existing behavior; zero bugs were found by any of the new tests. 15 suites / 64 tests, all green, independently re-verified (not just Codex's self-report).

---

### D33 — 2026-09-06 — Phase 1 quality gate audit found and fixed a genuine CSRF gap on the media upload route

**Decision:** While auditing CSRF coverage across every mutating route as part of task 22's quality-gate pass, `POST /api/v1/media/uploads` was the sole route in the entire backend that never called `common/Csrf.kt`'s `requireCsrfHeader()` — every other `POST`/`PATCH`/`DELETE` route in the codebase does, and that file's own docstring states "Every POST/PATCH/PUT/DELETE route must call this." `MediaIntegrationTest.kt`'s own `upload()` test helper already sent the `X-Requested-With: mentora-web` header on every call (evidently written assuming the check would be enforced), so this was a genuine, previously-undetected gap in the route handler itself, not a deliberate exemption — confirmed by there being zero mention of dropping CSRF for uploads anywhere in D21-D26 (the media module's own decision record). Fixed by adding the same one-line `call.requireCsrfHeader()` at the top of the `/uploads` handler, matching the pattern already used everywhere else, plus one new regression test (`upload without the CSRF header is rejected`, asserting `403 FORBIDDEN_CSRF`) — no existing test needed changes since they already sent the header.

**Why:** A cookie-authenticated state-changing endpoint without CSRF protection is a real, if narrow, security gap (Web's actual auth mechanism is the httpOnly cookie per `INTEGRATION_CONTRACT.md § 8a` — a cross-site form could otherwise trigger an authenticated upload). Finding and fixing exactly this kind of issue is what a quality-gate pass is for.

**Impact:** No client-visible contract change — any client already sending the header (as documented in `INTEGRATION_CONTRACT.md`'s conventions and as every existing test already did) is unaffected. 65 tests / 15 suites, all green, independently re-verified.

---

### D34 — 2026-09-06 — Seed/demo data script (task 20) implemented backend-natively, not at `infra/docker/mongo-init`

**Decision:** `architecture/TESTING_STRATEGY.md § 7` and `architecture/REPOSITORY_STRUCTURE.md` describe seed data living at `infra/docker/mongo-init/` — that directory is not created. Per D1, this project runs MongoDB as a native local service, not Docker, for the whole of Phase 1 (backend-only); there is no `infra/` directory and nothing to wire a mongo-init script into yet. Building that path now would be an inert scaffold matching a full-project (Web/mobile-included) layout that doesn't exist yet. Instead: a backend-native Kotlin entry point (`SeedData.kt`, `fun main()`) invoked via a new Gradle `seedDemoData` task (`build.gradle.kts`). It seeds through the real service layer wherever a write path already exists (`AuthService.register`, `CategoryService.create`, `CourseService.create/addSection/addLesson/publish`, `MediaService.upload`, `QuizService.replace`) — never a second, drifting document-writing implementation — with two narrow, justified exceptions: the Instructor/Admin role-flip (direct one-field Mongo update, the same established pattern every integration test's `provision()` helper already uses, since `AuthService.register` always creates `role: student` by design) and the Learning Path (direct collection insert, since `learningpaths/repository/LearningPathRepository.kt` has no create method at all — by design, per `product/PRODUCT_SPEC.md § 12`'s "no dedicated authoring UI in MVP"). Idempotent via existence pre-checks (by email/category name/course title/quiz-course-id/path-title) — safe to run repeatedly, verified twice (first run created 6 accounts/4 categories/6 courses/1 quiz/1 learning path; second run reported "already exists, no changes made").

Seeds: 6 accounts (1 Admin, 2 Instructor, 3 Student — `@mentora.dev`, password `MentoraDemo1` for all, printed to console on every run), 4 categories, 6 courses (4 Published/2 Draft, split across `en`/`ar` content languages with real Arabic course text, not placeholder strings), one course with a 3-question quiz, one Learning Path bundling 3 published courses. Thumbnails/lesson videos use small synthetic byte content uploaded through the real `MediaService.upload` path (not real video files) — appropriate for a backend-only phase with no client yet to actually play one.

**Why:** Matches the same category of implementation-time gap-filling as D1/D5 (adapting a full-project-scope locked doc to Phase 1's actual, smaller scope without contradicting its intent) rather than inventing infrastructure this phase doesn't otherwise need.

**Impact:** `backend/README.md` documents the `seedDemoData` task and the exact demo credentials. Independently re-verified beyond the self-report: fresh `gradlew.bat test`/`build` green (65 tests, 15 suites), ran `seedDemoData` myself against the local dev database, confirmed via `mongosh` (6 users, 4 categories, 6 courses [4 published/2 draft], 1 quiz, 1 learning path), and did a real end-to-end check — started the actual server, logged in as the seeded `admin@mentora.dev` account over HTTP, got back a valid access token and correct role.

---

### D35 — 2026-09-06 — Phase 2 token pipeline (task 1) is a plain Node script, not Style Dictionary

**Decision:** `tools/token-pipeline/generate.js` is a hand-written ~250-line Node script (no `style-dictionary` dependency) that reads `design-system/design-tokens.json` + `themes/theme-{light,dark}.json` and generates `web/styles/tokens.css`, `web/styles/tailwind-theme.css` (Tailwind v4's CSS-first `@theme`/`@theme inline` mapping), and `web/src/lib/design-tokens.generated.ts`. It resolves `component.*`'s `{ref}` aliases and `A+B@opacity`/`B@opacity` composite expressions (documented in `DESIGN_SYSTEM.md` v1.3.2's changelog) into `var()`/`color-mix()` CSS directly.

**Why:** `architecture/adr/ADR-011-design-token-pipeline.md` names Style Dictionary as the transform tool. Style Dictionary v5 (current on npm) has no built-in understanding of Mentora's specific `{ref}`/`A+B@opacity` composite syntax — a custom transform would have to be written either way, and evaluating/learning v5's newer plugin API (a real behavior change from the version the ADR's authors likely had in mind) for a same-amount-of-custom-code result wasn't a good trade against just writing the resolver directly, especially with no Android/iOS output target needed yet in this phase. ADR-011's actual, load-bearing requirements — single hand-authored source (`design-tokens.json`), generated-never-hand-edited output (every generated file is header-stamped and CI-diffable via `tools/token-pipeline/package.json`'s `check` script), deterministic regeneration — are all satisfied.

**Impact:** If/when Android/iOS token output is needed (Phase 3+), this script gains additional output-target functions the same way it already has three (`tokens.css`, `tailwind-theme.css`, `design-tokens.generated.ts`) — not a rewrite. If a future reviewer wants literal Style Dictionary conformance with ADR-011's letter, migrating is straightforward since the source JSON and the resolution logic (ref → token path → value) stay the same either way. Not a reversal of ADR-011's intent, recorded here per that ADR's own migration-path spirit.

---

### D36 — 2026-09-06 — `POST /auth/{login,register}`'s embedded `user` object is a narrower shape than `GET /users/me`, undocumented in INTEGRATION_CONTRACT.md § 8a

**Decision:** Web's `lib/auth/types.ts` defines two separate types: `AuthUser` (full profile — `id, email, name, role, avatarMediaId?, preferredLocale?, createdAt`, matching `GET /users/me`) and `AuthSessionUser` (`id, email, name, role, preferredLocale?` — no `avatarMediaId`, no `createdAt`), the latter for the `user` field inside `POST /auth/login`/`POST /auth/register` responses.

**Why:** Verified against `backend/src/main/kotlin/com/mentora/backend/auth/service/AuthService.kt`'s `AuthUser` Kotlin DTO (a same-named but different/narrower type than the `users` module's own response DTO) and confirmed live via `curl` through the Next.js proxy: registering a new account returns `{"id","email","name","role"}` only, no `avatarMediaId`/`createdAt`. `execution/INTEGRATION_CONTRACT.md § 8a` documents the token-stripping rule for this response but doesn't spell out the `user` object's exact field set, and the two Kotlin `AuthUser` types sharing a name (one in `auth`, a different one implicitly in `users`) is an easy trap for any client to fall into by assuming one shape everywhere. Also confirmed: both DTOs omit null-valued optional fields entirely rather than sending `null` (backend `explicitNulls = false`, D20) — so `avatarMediaId?`/`preferredLocale?` are `?:` optional, not `| null`, on the TypeScript side.

**Impact:** `login()`/`register()` (`lib/auth/actions.ts`) return `AuthSessionUser`; only `getCurrentUser()` (`GET /users/me`) returns the full `AuthUser`. The Login/Register forms invalidate (not optimistically set) the `useCurrentUser` TanStack Query cache after a successful auth call, rather than seeding it with the narrower session-user shape, so any consumer of `useCurrentUser` can keep relying on the full `AuthUser` type without a runtime/type mismatch. Worth a one-line addition to `INTEGRATION_CONTRACT.md § 8a` the next time that file is touched, so later phases (Android/iOS) don't rediscover this the same way.

---

### D37 — 2026-09-06 — Approved, additive backend change (task 3): `instructorName` denormalized onto `CourseSummary`/`CourseResponse`

**Decision:** Phase 1's `courses` module exposed only `instructorId` (a raw id) on every course DTO — no public endpoint anywhere resolves it to a display name, verified by reading `CourseService.kt` directly. `SCREEN_UX_SPECS.md`'s Course Details (`InstructorCard`) and every `CourseCard` require an instructor name. Rather than build the Explore/Course Details screens without it or fake it with an id/email, **explicit user approval was obtained** (this is a completed Phase 1 module PHASE_HANDOFF.md says not to rebuild — modifying it at all required asking first, unlike routine Phase 2 frontend work) for a minimal, additive backend change:
1. `UserRepository.findByIds(ids: List<ObjectId>): List<UserDocument>` — bulk `$in` lookup, copying the exact pattern already established by `AdminRepository.usersByIds`.
2. `UserService.getNamesByIds(ids: List<ObjectId>): Map<ObjectId, String>` — the service-layer wrapper `courses` calls, matching the existing cross-module reuse convention (`CourseService.requireOwnership`/`EnrollmentService.requireEnrollment`, PHASE_HANDOFF.md § 8).
3. `CourseService` gained a `UserService` constructor dependency (Koin-wired, `CoursesModule.kt`) and now resolves `instructorName: String` — one new field, added to both `CourseSummary` and `CourseResponse` — via a bulk lookup in `list()` (avoiding N+1 across a page) and a single-id lookup (`toResponseResolved()`) at every other call site that returns a `CourseResponse`.

**Why:** No route changed, no existing field changed or removed, no schema/collection change — purely additive on top of an already-complete module, using patterns that already existed elsewhere in the codebase (not inventing a new cross-module-access style). The alternative (omitting instructor name from Explore/Course Details) would have shipped a visibly incomplete core screen; a raw id/email stand-in would have looked broken in the demo.

**Impact:** Two test files needed trivial updates for the new required constructor param / DTO field (`CourseServiceTest.kt` — added a mocked `UserService` stub; `ProgressServiceTest.kt` — added `instructorName` to a directly-constructed `CourseResponse` fixture). Full backend suite re-verified after the change: 65 tests, 0 failures, 0 errors (`./gradlew.bat test`), plus a live `curl` check against seeded data confirming `instructorName` on the wire (e.g. `"instructorName": "Omar Khalil"`). `execution/INTEGRATION_CONTRACT.md`'s Courses section is updated to document the new field. No other Phase 1 module or route was touched.

---

### D38 — 2026-09-06 — Standing gotcha for every Web API module: nullable Kotlin fields are *omitted*, never sent as `null`

**Decision:** Every `lib/api/*.ts` module must treat an optional backend field as **absent** (TypeScript `field?: T`) and check it with `!== undefined`/truthy, never `!== null`/`=== null`. Caught live twice already: `LearningPathResponse.progressPercent` (a guest viewing a path got `progressPercent` missing from the JSON entirely; a strict `!== null` check in `learning-path-details-screen.tsx` evaluated `undefined !== null` as `true` and rendered a bogus 100%-looking progress bar) and `AuthUser`/`AuthSessionUser` (D36).

**Why:** The backend's `explicitNulls = false` kotlinx.serialization config (D20) applies to every module, not just the ones already hit — this isn't an auth-specific or learning-paths-specific quirk, it's global backend behavior every future `lib/api/*.ts` module will run into the first time it types a nullable Kotlin field as `T | null` and compares strictly against `null`.

**Impact:** Fixed `LearningPathResponse.progressPercent` to `progressPercent?: number` and the corresponding check to `!== undefined`. Audited every other strict-null comparison in `web/src` at the same time (grep for `=== null`/`!== null`) — none remained. Any new API module should default to `field?: T` (optional) rather than `field: T | null`, and use truthy/falsy or `!= null`/`== null` checks, not strict `null` comparison — noted here so this isn't rediscovered a third time.

---

### D39 — 2026-09-06 — `CourseCard`/`LearningPathCard` need an auth-aware `basePath` prop; found via live browser testing of task 4

**Decision:** `CourseCard` and `LearningPathCard` take an optional `basePath` prop (default `""`), used to build their link href (`${basePath}/courses/:id`, `${basePath}/paths/:id`). `ExploreScreen`, `LearningPathsScreen`, and `LearningPathDetailsScreen` call `useCurrentUser()` and pass `basePath="/app"` when a Student is logged in, `""` otherwise. Checkout's own Cancel action links to `/app/courses/:id` directly (checkout is auth-only, so there's no ambiguity there).

**Why:** Both card components hardcoded the Guest-facing path (`/courses/:id`, `/paths/:id`). Since `PublicNavbar` (used only by the `(public)` route tree) unconditionally renders "Login"/"Get Started" with no auth check, a logged-in Student clicking a course from the authenticated `/app/explore` screen landed on `(public)/courses/:id` — correct content (Course Details itself is auth-aware and showed "Enroll") but a contradictory Guest-styled navbar above it. Caught live while browser-testing task 4's checkout entry point (Explore → Course Details → Checkout).

**Impact:** Fixed for the three shared discovery screens (Explore, Learning Paths, Learning Path Details) — the paths a logged-in Student actually takes to reach Course Details/checkout. **Not fixed:** the Landing page's own `CourseCard`/`LearningPathCard` usage (`app/[locale]/(public)/page.tsx`) still always links to the public path — Landing is a Server Component with no cheap server-side auth check, and it's conceptually the Guest home; a logged-in user manually browsing back to `/` is an edge case, not a flow any screen currently drives them into. There is still no dedicated authenticated top-nav component at all (`/app` pages render bare, chrome-less `<main>` — see `dashboard-shell.tsx`) — that's a known gap for whichever future task builds the real authenticated shell, not something task 4 scoped in.

---

### D40 — 2026-09-06 — Task 5: real Sidebar shell for every `/app/*` page; hand-drawn icon placeholder; My Learning/Dashboard compose existing endpoints (no new backend work)

**Decision:** Three related calls made while building task 5 (Student Dashboard + My Learning):

1. **Sidebar shell built as an `app/[locale]/app/layout.tsx` wrapping ALL `/app/*` pages**, not just Dashboard. `ux/WEB_UX.md` ("Authenticated pages do not show the public Navbar — the Sidebar replaces it") makes clear every authenticated page needs this chrome, not only the one task 5 was nominally about. `AppShell` (`components/navigation/app-shell.tsx`) now owns the single `<main id="main-content">` — every existing `/app/*` page file (`explore`, `paths`, `paths/[id]`, `courses/[id]`, `checkout/[id]`, `checkout/[id]/success`, from tasks 3-4) had its own redundant `<main>` wrapper stripped in the same pass, since a second `id="main-content"` per page would have been invalid HTML. `dashboard-shell.tsx`'s smoke-test placeholder (logout button proving the session loop) was deleted; its logout logic moved into `AppShell`'s bottom nav item.
2. **Icon set is a small hand-drawn inline-SVG set** (`components/ui/icon.tsx`), not the real self-hosted Material Symbols Rounded variable font `design-system/DESIGN_SYSTEM.md` specifies. No font asset/build pipeline for that font exists in this repo, and fetching one wasn't practical mid-task. The 10 icons needed for the Sidebar (dashboard, explore, myLearning, learningPaths, aiTutor, certificates, profile, settings, menu, logout) approximate Material Symbols Rounded's rounded-stroke silhouette but are not the exact glyphs.
3. **`useMyLearning`/`useFollowedLearningPaths` compose existing endpoints client-side (N+1)** rather than proposing a new aggregate backend endpoint the way D37 did for `instructorName`. `GET /enrollments` has no course display fields; `GET /learning-paths` (the list) has no `isFollowing`/`progressPercent` (only the per-path detail endpoint does). Both hooks fetch the list, then fan out to per-item detail queries via `useQueries`.

**Why:** (1) Building the Sidebar as a one-off addition to just the Dashboard page would have left every other `/app/*` page still chrome-less, an inconsistency the design system doesn't allow — the spec is unambiguous that Sidebar is universal authenticated chrome, not a Dashboard-specific widget. (2) A real Material Symbols asset pipeline is a meaningfully separate piece of work (font subsetting/self-hosting or a real SVG sprite sourced from the actual icon set) that would have blocked the whole task; a placeholder behind a stable component API defers that cost without blocking navigation from existing. (3) At the seed data's actual scale (a handful of courses/paths total), N+1 is simpler and lower-risk than another cross-cutting backend change, and D37's bar for "propose a backend addition" is about correctness gaps (a field literally not exposed anywhere), not about request-count optimization.

**Impact:** Every `/app/*` route now has real chrome (Sidebar + slim top bar) instead of bare content — this also retroactively fixes tasks 3-4's pages, which had none. The collapse preference persists via `localStorage` (a UI convenience, not session-critical state). **Known follow-ups, not blocking:** (a) swapping in the real Material Symbols font is a drop-in replacement behind `Icon`'s existing API — no call sites change. (b) The mobile off-canvas drawer (`mobileOpen` state, `tablet:hidden` scrim) was written to match `ux/RESPONSIVE_BEHAVIOR.md § 1` exactly but **could not be verified live** — the browser-automation environment's window-resize call reported success without changing the rendered viewport, so only desktop/tablet-width behavior (Sidebar collapse toggle, RTL mirroring) was confirmed by real browser interaction; the mobile breakpoint path is verified by code review only. (c) `ux/WEB_UX.md`'s "Sidebar collapses by default on Course Player" isn't implemented yet — `AppShell`'s `collapsed` state is a manual user toggle only; task 6 (Course Player) needs to decide how a specific screen influences shell-level collapse state.

---

### D41 — 2026-09-06 — Task 6 (Course Player + Quiz + Quiz Results) delegated to Codex via the `codex-delegate` skill; verified and landed by Claude

**Decision:** Per the user's explicit request, task 6's frontend implementation was delegated to Codex (the `codex-delegate` skill, matching Phase 1's Claude-supervises/Codex-implements pattern from `MASTER_IMPLEMENTATION_PLAN.md`), not hand-written directly. Before writing the brief, Claude independently read the real backend source (`ProgressRoutes.kt`/`ProgressService.kt`, `QuizRoutes.kt`/`QuizService.kt`, `MediaRoutes.kt`/`MediaService.kt`, `CertificateService.kt`) to verify every endpoint contract, request/response shape, and the `explicitNulls=false` nullability of each field, rather than assuming from the frontend side — the brief embedded these as verified facts, not assumptions. Codex's single run produced: `lib/api/{quiz,media}.ts` + extensions to `progress.ts`; `VideoPlayer`/`QuestionCard`/`AnswerOption` components; `CoursePlayerScreen`/`QuizScreen`/`QuizResultsScreen`; the three `/app/learn/:id[...]` routes; a `SidebarForceCollapseContext` addition to `app-shell.tsx` (page-scoped Sidebar auto-collapse on Course Player/Quiz, per `ux/WEB_UX.md`, without touching the user's saved localStorage preference); new icons; and the full CSS/i18n additions — all in one pass, on the first try, with no rework cycle needed.

**Why delegate this one:** task 6 is exactly the profile `codex-delegate` and the master plan's Claude/Codex allocation table call out — large, code-heavy, mechanical once the contract is nailed down (a custom video player against a fully-specified design-system table, a quiz state machine against fully-specified endpoints), with no architecture decisions Codex needed to make that weren't already pinned down in the brief.

**Verification performed (not just trusting Codex's self-report):** independently re-ran `typecheck`/`lint`/`lint:logical-properties`/`build` from a clean `.next` (all matched Codex's own claimed output exactly, including the pre-existing 5 `<img>` warnings — no new ones); read every touched file's diff against `git status`/`git diff`, confirming zero out-of-manifest changes and zero unbriefed edits to existing tests or logic; cross-checked the new `--component-video-player-*`/other CSS custom properties Codex referenced against `web/styles/tokens.css` to confirm none were invented (all pre-existed from the token pipeline, the same pattern already seen for Checkout/SuccessState/Sidebar's component-layer tokens in earlier tasks); then a full live browser walkthrough logged in as the seeded `student1` — completed both lessons of a real enrolled course, watched the Sidebar auto-collapse on entering Course Player and un-collapse on leaving, took the course's real 3-question quiz end-to-end (selection → next → submit), saw the graded results breakdown render with correct per-option success/error styling, clicked through to the completion confirmation (certificate auto-issued server-side), and confirmed My Learning/Dashboard stats updated correctly afterward — plus an Arabic RTL spot-check of the completion screen.

**One deviation surfaced by live testing, not by Codex:** the brief told Codex to expect the video area's `error` event to fire, since the seed data's lesson "videos" are literal text bytes (`backend/src/main/kotlin/com/mentora/backend/SeedData.kt` uploads `"seed-video-<title>"` as the file content, discovered by reading that file directly before writing the brief). In this Chrome build, the fake payload instead loads as an inert zero-duration video (controls render, play/pause is a no-op, no crash) rather than firing a hard decode error — so the polished inline error state built for that path exists and is wired correctly but was not observed firing live. Not a code defect: the real `onError` handler is present and correct: this is a fact about how this specific fake payload happens to behave in this browser, not something to build around.

**Impact:** Course Player, Quiz, and Quiz Results are done, matching `design-system/COMPONENTS.md`'s VideoPlayer/QuestionCard/Answer-Options/Result-screen specs precisely (including token-math tricks to hit exact pixel specs like the 52px answer-option row and 44px touch targets purely from `--space-*` composition, never a hardcoded pixel value). Certificate issuance is confirmed working end-to-end even though Certificates List/Detail (task 7) isn't built yet — "View Certificate" is a forward-reference link to `/app/certificates`, same established pattern as every prior task's forward link to not-yet-built routes.

---

### D42 — 2026-09-06 — Task 7 (Certificates List + Detail): no real certificate asset/rendering engine exists, so both screens build a token-driven placeholder presentation instead of an image

**Decision:** Task 7 was also delegated to Codex (`codex-delegate`, same as D41) and landed after the same independent gate re-run + diff review + live browser verification. One judgment call worth recording: `design-system/COMPONENTS.md`'s `CertificateCard` spec calls for a "thumbnail/preview" image, but this backend has no certificate-image generation or storage at all (`CertificateDetailResponse` is pure text data — student/course/instructor names and dates, no image URL of any kind). Both the list card's preview and the detail screen's "document" build a polished, fully token-driven placeholder instead (a bordered panel with a decorative inset frame, the `certificates` Sidebar icon already built in task 5, and the course title) rather than pointing an `<img>` at a nonexistent endpoint or fabricating a fake image URL.

**Why:** Pointing `<img>` at a URL the backend doesn't serve would either 404 visibly or require inventing a fake asset path that looks like real backend capability when it isn't — worse than an honest, well-designed placeholder built from tokens already in the system. The detail screen goes a step further than a bare data list, presenting an actual "Certificate of Completion — Awarded to [name] — for successfully completing [course]" layout, which is closer to the product intent ("a viewable, shareable certificate artifact," `product/USER_FLOWS.md` § 18) than a plain field-by-field dump would have been.

**Impact:** Certificates List (`/app/certificates`) and Certificate Detail (`/app/certificates/:id`, `:id` being the backend's public `MTR-XXXX-...` id format, passed through verbatim) are done. Verified live end-to-end: the certificate auto-issued during task 6's live quiz-completion test appears correctly in the list and detail view, in both en and ar, including the not-found path for an invalid/foreign certificate id (a distinct `EmptyState`-style message, not `ErrorState`, matching the same not-found-vs-error distinction already established for quiz attempts in task 6). "Share" is confirmed UI-only — a local 2-second acknowledgment label swap, zero network/clipboard/Web-Share-API calls, per `product/DEMO_PAYMENT_FLOW.md`'s sibling "UI-only affordance" language for this exact kind of demo action. No backend changes needed or made.

---

### D43 — 2026-09-06 — Task 9 (AI Tutor chat UI, streaming): dedicated raw-`fetch` stream reader instead of the `apiFetch`/`apiRequest` JSON-envelope abstraction; delegated to Codex, verified live

**Decision:** Task 9 was delegated to Codex via `codex-delegate` (same pattern as D41/D42). Before writing the brief, Claude read the real backend source (`AiTutorRoutes.kt`/`AiTutorService.kt`, `AiProvider`/`StubAiProvider`) to confirm the message-send endpoint (`POST /ai-tutor/conversation/messages`) responds with `respondTextWriter` — a genuinely chunked `text/plain` stream, architecturally unlike every other endpoint's JSON envelope — and that `StubAiProvider` always returns one fixed placeholder sentence streamed word-by-word with a 30ms delay (no real LLM call yet). Because this response shape cannot go through the existing `apiFetch`/`apiRequest` abstraction (which assumes and parses a JSON envelope), Codex built a dedicated `streamAiMessage()` in `lib/api/ai-tutor.ts` that manually replicates the CSRF/`credentials: "same-origin"` convention via raw `fetch()`, then consumes the body with `response.body.getReader()` + `TextDecoder` (`{stream: true}` per chunk, a final flush decode after EOF) rather than `response.json()`. 401-refresh-retry (the standard behavior for every other endpoint via `apiFetch`) is explicitly NOT implemented for this one call — a deliberate, disclosed scope reduction, not a gap, since retrying a partially-streamed body isn't meaningful the way retrying a JSON GET is.

**Why:** Forcing a streaming `text/plain` response through a client built around parse-the-whole-body-as-JSON would have meant either buffering the whole stream before returning (defeating the purpose of chunked delivery) or bolting a special-case branch into the shared client that every other call site would have to reason about. A small, self-contained function scoped to this one endpoint keeps the blast radius local and matches the reality that this is the one architecturally different endpoint in the app.

**Verification performed:** independently re-ran `typecheck`/`lint`/`lint:logical-properties`/`build` from a clean `.next` (all matched Codex's own claimed output exactly); read every touched/created file in full (`ai-tutor.ts`, `ai-tutor-bubble.tsx`, `ai-tutor-quick-action.tsx`, `ai-tutor-screen.tsx`, CSS additions, i18n key parity en/ar); confirmed the new `.mtx-ai-tutor-*` CSS uses only pre-existing tokens and correct logical properties (`border-end-start-radius`/`border-end-end-radius` for the message-tail corners, `justify-content: flex-start`/`flex-end` for AI-vs-user alignment) — no invented custom properties. Live browser verification as the seeded `student1`: sent a typed message and a quick-action chip, watched the reply actually stream and complete (`StubAiProvider`'s fixed placeholder sentence rendered correctly, thinking-state and completion both observed), then loaded `/ar/app/ai-tutor` and confirmed RTL mirrors correctly — user/AI bubble sides swap sides via the logical properties (no separate RTL-specific styling needed), sidebar stays visible (task 9 does not force-collapse it, unlike Course Player/Quiz — confirmed correct per `ux/WEB_UX.md`), and Arabic strings render for title/quick-actions/composer. (Session note: the first load attempt hit a stale 401 from a dev-server restart mid-session, not a Task 9 defect — re-login resolved it immediately.)

**Impact:** AI Tutor chat (`/app/ai-tutor`) is done against the live stub backend. No backend changes needed or made. The real Anthropic Claude integration behind `AiProvider` remains explicitly out of scope for this phase (backend scaffold only, per Phase 1's M13 boundary) — the frontend chat UI is fully functional against whichever provider implementation the backend wires in later, since the contract (chunked `text/plain` over the same endpoint) does not change.
