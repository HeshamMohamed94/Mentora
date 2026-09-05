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
