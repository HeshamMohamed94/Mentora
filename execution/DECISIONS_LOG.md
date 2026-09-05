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
